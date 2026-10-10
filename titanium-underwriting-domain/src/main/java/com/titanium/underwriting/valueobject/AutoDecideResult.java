package com.titanium.underwriting.valueobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

/**
 * 自动决策结果（g02-04 / AC-05）
 * <p>
 * 粗粒度自动决策端点的统一出参：无论本次是「新建后决策」还是「幂等复用既有结论」，都返回同一形状。
 * </p>
 * <p>
 * 🔴 <b>字段刻意扁平、且按「落位后的列」而非「一个原始 reason」组织</b>：
 * <ul>
 *   <li><b>不承载 {@link ExtraPremium} 值对象</b>——本结果有两个来源：刚决策完的
 *       {@code UnderwritingDecidedEvent}（携带强校验的 {@code ExtraPremium}）与幂等命中时的读模型
 *       {@code UnderwritingQueryResult}（只有扁平的加费列）。若承载值对象，读模型侧须从四个扁平列重建，
 *       而该值对象紧凑构造器强制「比例型须 ratio&gt;0、固定额型须 fixedAmount&gt;0」，历史数据一旦缺项
 *       即构造失败——为一条读取路径引入构造期异常不划算。</li>
 *   <li><b>不合成单一 {@code reason}</b>——决策事件只带一个 {@code reason}，落到读模型时按状态分派进
 *       {@code rejectReason}/{@code reviewComments}/{@code exclusionReason} 三列（见 query 层
 *       {@code UnderwritingViewMapper} 与 web 层 {@code UnderwritingWebMapper} 的 {@code @Named} 转换，
 *       两处同口径）。本结果沿用该列级形态，使「事件来源」与「读模型来源」同样直填、
 *       无需把已分派的列反向还原成 reason。</li>
 * </ul>
 * </p>
 * <p>
 * ⚠️ 因上述「事件 → 三列」的分派规则在投影侧与本结果装配侧各有一份实现，二者口径须一致 ——
 * 由 g02-05 的核保结论双通道一致性守卫锁死（同一核保单的事件通道与读模型通道须给出同一结论）。
 * </p>
 *
 * @param underwritingId          核保单ID
 * @param insuranceId             投保单号（幂等键，原样回显供跨域对账）
 * @param status                  核保状态
 * @param conclusionType          核保结论类型（接受/修改条件/拒绝/延期；未决策时为空）
 * @param riskLevel               风险等级（未决策时为空）
 * @param riskScore               综合风险评分
 * @param extraPremiumType        加费类型 code（无加费时为空）
 * @param extraPremiumRatio       加费率（比例加费时有值）
 * @param extraPremiumFixedAmount 固定加费额（固定额加费时有值）
 * @param extraPremiumReason      加费原因
 * @param rejectReason            拒保原因（拒保/撤单结论时有值）
 * @param reviewComments          审核意见（转人工等非拒保结论时有值）
 * @param exclusionReason         除外原因（除外承保结论时有值）
 * @param decidedBy               决策人
 * @param decidedAt               决策时间（未决策时为空）
 * @param auditType               核保方式（自动/人工/混合）
 * @param tenantId                租户ID
 * @author wei.sun
 * @since 2026/9/27
 */
public record AutoDecideResult(String underwritingId, String insuranceId,
                               UnderwritingEnum.UnderwritingStatus status,
                               UnderwritingEnum.ConclusionType conclusionType,
                               UnderwritingEnum.RiskLevel riskLevel, Integer riskScore,
                               String extraPremiumType, BigDecimal extraPremiumRatio,
                               BigDecimal extraPremiumFixedAmount, String extraPremiumReason,
                               String rejectReason, String reviewComments, String exclusionReason,
                               String decidedBy, LocalDateTime decidedAt,
                               UnderwritingEnum.AuditType auditType, String tenantId) {
}
