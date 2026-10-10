package com.titanium.underwriting.repository;

import java.util.Optional;

import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保单事件流只读出口（与聚合平级）。
 *
 * <p>
 * 提供核保单在<b>事件流</b>（写侧权威、无投影延迟）上的两个强一致判据：该聚合<b>是否已创建</b>、
 * 以及<b>最近一次结论</b>是什么。粗粒度自动决策端点 {@code :auto-decide} 以投保单号为幂等键，
 * 其幂等判定若只依赖 CQRS 读模型（{@code t_underwriting_view}）就会失真——读模型由<b>异步投影</b>维护，
 * 上游 Saga 在首次调用返回后立即重试时投影尚未追平：
 * </p>
 * <ul>
 *   <li>读模型<b>有行但状态陈旧</b>（仍显 {@code PENDING}）⇒ 编排器误判「在办」而续跑，聚合终态守卫拒绝，
 *       调用方收到 409 非法状态流转（真机实测：66ms 间隔重放即命中）；</li>
 *   <li>读模型<b>连行都还没有</b> ⇒ 编排器误判「无既有核保单」而再建一张（真机实测：210ms 间隔重放即产生
 *       两张核保单）。</li>
 * </ul>
 * <p>
 * 二者都违反契约「同一投保单重复调用返回同一张核保单的结论（上游 Saga 重试/重入安全）」。
 * </p>
 *
 * <p>
 * 🔴 <b>为何读事件流而不是读聚合</b>：{@code Underwriting} 聚合的 {@code @EventSourcingHandler} 只重建
 * {@code status}/{@code conclusionType}/{@code riskLevel}/{@code extraPremium} 与三类原因列，
 * <b>不保留</b> {@code riskScore} 与 {@code auditType}。改由聚合装配会让复用通道缺两个字段，破坏
 * {@code AutoDecisionAssembler} 的核心承诺「事件通道与读模型通道同形」。事件流既强一致、字段又完整，
 * 且可直接复用现成的 {@code toResult(UnderwritingDecidedEvent)}，同形由代码结构（同一装配器）保证而非靠人工对齐。
 * </p>
 *
 * <p>
 * 🔴 <b>失败降级方向：两个方法都不得导致「静默产生第二张核保单」</b>。事件流读取失败时——
 * {@link #exists} 返回 {@code true}（无法证伪存在 ⇒ 按存在处理），{@link #findLatestDecision} 返回空。
 * 二者殊途同归：编排器都会走「续跑」而非「新建」，由后续命令以显式异常失败（调用方可重试），
 * 而非悄悄多建一张核保单（业务上不可接受的重复核保）。
 * </p>
 *
 * <p>
 * 实现由 infrastructure 层经 Axon {@code EventStore} 提供（{@code infrastructure/repository}），
 * 领域与应用层不感知事件存储实现。
 * </p>
 */
public interface UnderwritingEventStreamRepository {

    /**
     * 判定该核保单是否已在事件流中创建
     *
     * <p>
     * 与「读模型里有没有这张单」是<b>两回事</b>：读模型滞后期间事件流已有、读模型尚无，本方法即为此窗口而设。
     * </p>
     *
     * @param underwritingId 核保单ID
     * @return 事件流中已有该聚合的事件则 {@code true}；ID 缺失时 {@code false}；读取失败时 {@code true}（见类注释降级方向）
     */
    boolean exists(UnderwritingId underwritingId);

    /**
     * 取该核保单最近一次核保决策事件
     *
     * <p>
     * 核保单一经出具结论即进入终态、不可再被改写，但同一保单在保全加保等场景可经新的核保单再次决策，
     * 故取<b>最后一条</b>而非第一条——早先的结论不是当前结论。
     * </p>
     *
     * @param underwritingId 核保单ID
     * @return 最近一次决策事件；从未出具过结论（或事件流读取失败）时为空
     */
    Optional<UnderwritingDecidedEvent> findLatestDecision(UnderwritingId underwritingId);
}
