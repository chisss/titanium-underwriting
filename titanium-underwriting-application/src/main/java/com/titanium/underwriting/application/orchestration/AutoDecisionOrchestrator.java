package com.titanium.underwriting.application.orchestration;

import java.util.List;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.application.orchestration.assembler.AutoDecisionAssembler;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.generator.UnderwritingNoGenerator;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.repository.UnderwritingEventStreamRepository;
import com.titanium.underwriting.valueobject.AutoDecideRequest;
import com.titanium.underwriting.valueobject.AutoDecideResult;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 自动决策编排器（粗粒度，g02-04 / AC-05）
 * <p>
 * 把「创建核保 → 提交输入 → 出具决策」三步同步命令式编排收敛为一次调用，并以内置幂等键
 * （投保单号）判「复用 / 续跑 / 新建」三分支：
 * <ol>
 *   <li><b>读模型已显示终态核保单</b> → 直接复用其结论，<b>不再触发任何命令</b>（幂等命中的快路径）；</li>
 *   <li><b>读模型显示在办核保单</b> → 先经 {@link UnderwritingEventStreamRepository} 用事件流
 *       <b>强一致复核</b>：事件流已有结论则同样复用（投影滞后窗口），确属在办才续跑；</li>
 *   <li><b>读模型查无此投保单的核保单</b> → 以<b>由投保单号派生的确定性案件号</b>
 *       （{@link UnderwritingId#forAutoDecide(String, String)}）在事件流上终判「复用 / 续跑 / 新建」。</li>
 * </ol>
 * </p>
 * <p>
 * 🔴 <b>归属 application/orchestration</b>：本类发命令（{@link CommandGateway}）、协调聚合与外部查询，
 * 属应用编排而非领域逻辑（根规约 §3.4.4「编排器不属于 domain」）；同时它<b>不</b>实现任何 Adapter、
 * 不承载业务规则——风险评分/结论映射仍在聚合根与决策编排器内。
 * </p>
 * <p>
 * 🔴 <b>为何不调 {@code UnderwritingCommandService.createUnderwriting}</b>：后者是本层的入口门面，
 * 而门面的 {@code autoDecide} 又委托本编排器，反向依赖会构成构造器循环依赖（Spring 启动即失败）。
 * 故此处自行「取号 + 发命令」——与门面共用同一发号端口 {@link UnderwritingNoGenerator}，
 * 案号生成契约只有一处（端口内聚），本类只负责调用它。
 * </p>
 * <p>
 * 🔴 <b>投影滞后窗口（分支二、三为何读事件流）</b>：幂等判定若只看 CQRS 读模型，会同时输在两个窗口上——
 * 读模型<b>有行但状态陈旧</b>（仍显 {@code PENDING}）时误续跑，聚合终态守卫拒绝，调用方收到 409 非法状态流转
 * （真机实测：首次调用后 66ms 重放即命中）；读模型<b>连行都没有</b>时误判「无既有核保单」而再建一张
 * （真机实测：210ms 间隔重放产生两张核保单）。二者都违反契约「同一投保单重复调用返回同一张核保单的结论
 * （上游 Saga 重试/重入安全）」，故两条分支都以事件流（写侧权威、无投影延迟）为准。
 * </p>
 * <p>
 * 🔴 <b>分支三的强一致来自案件号本身，而非反查</b>：读模型滞后时按业务键反查必然落空（ES 聚合没有按业务键的
 * 索引），故不再依赖反查——改为让「同一投保单」<b>必然映射到同一聚合标识</b>：案件号由 (租户, 投保单号)
 * 派生，同键必得同号，幂等由<b>聚合标识本身</b>保证，不依赖任何一个索引。事件存储对
 * {@code (aggregate_identifier, sequence_number)} 的唯一约束（{@code uk_dee_agg_seq}）进一步保证
 * <b>并发同键也只可能成功创建一张</b>，落败方显式失败而非静默重复。手法与保全核保的
 * {@link UnderwritingId#forMaintenance(String, String)} 同型。
 * </p>
 * <p>
 * 📌 <b>并发同键的失败形态（真机实测，2026-09-27）</b>：两路同时提交同一投保单时，胜者 200 并落库一张，
 * 落败方在 {@code uk_dee_agg_seq} 上撞 {@code Duplicate entry '<案件号>-0'} → Axon 抛
 * {@code AggregateStreamCreationException}（{@code Cannot reuse aggregate identifier ... since identifiers
 * need to be unique}），经 {@code AbstractRetryScheduler} 重试 4 次仍失败（聚合标识已存在，重试不可能成功）
 * → HTTP 500。
 * <br>
 * 🔴 <b>这是设计内的语义边界，不得据此判定幂等失效</b>：契约承诺的是「不产生第二张核保单」，**不是**
 * 「并发两路都返回 200」。上游 {@code IssuanceSaga} 经 {@code sendAndWait} 同步调用，走的是**顺序**
 * 重放（命中分支一/二/三的复用腿，稳定 200），并发提交不在其正常路径上。
 * <br>
 * 🔴 <b>若将来确需并发双 200，正确做法</b>是：在 {@link #createUnderwriting} 捕获
 * {@code AggregateStreamCreationException} 后**回落到「查事件流 → 复用」**（即重入本类分支三的后半段）。
 * <b>错误做法</b>是给构造期处理器加 {@code @CreationPolicy(CREATE_IF_MISSING)}——那会让落败方的事件
 * **追加进胜者的聚合**，产生第二条 {@code UnderwritingCreatedEvent}，把「显式失败」换成「静默污染事件流」。
 * </p>
 * <p>
 * 📌 <b>残余边界（存量单）</b>：确定性案件号只管<b>本端点今后创建</b>的核保单。经旧四步路径
 * （创建 → 提交 → 决策，案件号为雪花号）建成的存量单，若其读模型行尚未投影出来，分支三仍会新建一张。
 * 该窗口只对存量单成立且随时间单调收敛（存量单的投影早已追平），不为它引入业务键索引表——那会打破本域
 * 「写侧纯事件溯源、零 JPA 写表」的定位，收益不抵代价。判定依据同根规约 §四.3
 * 「ES 聚合查投影读模型（最终一致，辅以数据库唯一约束/去重命令兜底）」，此处走的正是「唯一约束」那一支。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDecisionOrchestrator {

    /** 粗粒度自动决策固定走自动核保方式（与四步路径的出单主链路同口径） */
    private static final UnderwritingEnum.AuditType AUTO_AUDIT_TYPE = UnderwritingEnum.AuditType.AUTOMATIC;

    private final CommandGateway                    commandGateway;
    private final UnderwritingNoGenerator           underwritingNoGenerator;
    private final UnderwritingQueryAppService       underwritingQueryAppService;
    private final UnderwritingDecisionOrchestrator  underwritingDecisionOrchestrator;
    private final AutoDecisionAssembler             autoDecisionAssembler;
    private final UnderwritingEventStreamRepository underwritingEventStreamRepository;

    /**
     * 粗粒度自动决策：一次调用完成「创建/幂等复用 + 提交输入 + 出具决策」。
     *
     * @param request 自动决策请求（投保单号为幂等键）
     * @return 核保结论（新建与复用同形）
     */
    public AutoDecideResult autoDecide(AutoDecideRequest request) {
        List<UnderwritingQueryResult> existing = underwritingQueryAppService
                .findUnderwritingsByInsuranceId(request.insuranceId(), request.tenantId());

        // 分支一：读模型已显示终态 ⇒ 直接复用（快路径，不读事件流）
        UnderwritingQueryResult concluded = firstConcluded(existing);
        if (concluded != null) {
            log.info("[自动决策] 幂等命中已出结论的核保单，直接复用: insuranceId={}, underwritingId={}, status={}",
                    request.insuranceId(), concluded.getUnderwritingId(), concluded.getStatus());
            return autoDecisionAssembler.toResult(concluded);
        }

        // 分支二：读模型显示在办 ⇒ 用事件流强一致复核后再决定「复用 / 续跑」
        UnderwritingQueryResult open = firstOpen(existing);
        if (open != null) {
            Optional<UnderwritingDecidedEvent> prior = underwritingEventStreamRepository
                    .findLatestDecision(UnderwritingId.of(open.getUnderwritingId()));
            if (prior.isPresent()) {
                // 投影滞后窗口：读模型仍显在办，事件流已有结论。此处必须复用而非续跑——
                // 续跑会被聚合的终态守卫拒绝，调用方（上游 Saga 重试）会收到非法状态流转的错误
                log.info("[自动决策] 读模型滞后于事件流，按事件流结论幂等复用: insuranceId={}, underwritingId={}",
                        request.insuranceId(), open.getUnderwritingId());
                return autoDecisionAssembler.toResult(prior.get());
            }
            log.info("[自动决策] 命中在办核保单，续跑: insuranceId={}, underwritingId={}, status={}",
                    request.insuranceId(), open.getUnderwritingId(), open.getStatus());
            return submitInputAndDecide(open.getUnderwritingId(), request);
        }

        // 分支三：读模型查无此投保单的核保单（可能是真无，也可能只是投影还没建行）
        return resolveAbsentUnderwriting(request);
    }

    /**
     * 分支三：读模型查无此投保单的核保单时，以确定性案件号在事件流上终判「复用 / 续跑 / 新建」。
     * <p>
     * 🔴 三条出路的判据全部来自事件流（强一致），不来自读模型：之所以还能判「无既有核保单」，靠的不是
     * 反查到了什么，而是「同键必得同号」——同键重放必落回同一聚合标识，{@link UnderwritingEventStreamRepository#exists}
     * 一问即知。
     * </p>
     *
     * @param request 自动决策请求
     * @return 核保结论
     */
    private AutoDecideResult resolveAbsentUnderwriting(AutoDecideRequest request) {
        UnderwritingId targetId = UnderwritingId.forAutoDecide(request.tenantId(), request.insuranceId().value());
        if (!underwritingEventStreamRepository.exists(targetId)) {
            return submitInputAndDecide(createUnderwriting(targetId, request), request);
        }

        // 事件流已有该聚合 ⇒ 读模型只是尚未投影出来（同键重放的滞后窗口），按事件流结论分派
        Optional<UnderwritingDecidedEvent> prior = underwritingEventStreamRepository.findLatestDecision(targetId);
        if (prior.isPresent()) {
            log.info("[自动决策] 读模型尚无该投保单的行，但确定性案件号已出结论，幂等复用: insuranceId={}, underwritingId={}",
                    request.insuranceId(), targetId.value());
            return autoDecisionAssembler.toResult(prior.get());
        }
        log.info("[自动决策] 确定性案件号已建但未出结论，续跑: insuranceId={}, underwritingId={}",
                request.insuranceId(), targetId.value());
        return submitInputAndDecide(targetId.value(), request);
    }

    /**
     * 在既有核保单上完成「提交输入 + 出具决策」并装配结论
     *
     * @param underwritingId 目标核保单ID
     * @param request        自动决策请求
     * @return 核保结论
     */
    private AutoDecideResult submitInputAndDecide(String underwritingId, AutoDecideRequest request) {
        submitInput(underwritingId, request);
        UnderwritingDecidedEvent decided = decide(underwritingId, request);
        log.info("[自动决策] 完成: insuranceId={}, underwritingId={}, 结论={}, 风险等级={}, 加费率={}",
                request.insuranceId(), underwritingId, decided.conclusionType(), decided.riskLevel(),
                decided.extraPremium() != null ? decided.extraPremium().ratio() : null);
        return autoDecisionAssembler.toResult(decided);
    }

    /** 已有核保单中第一张已出结论（终态）者；无则返回 null */
    private UnderwritingQueryResult firstConcluded(List<UnderwritingQueryResult> candidates) {
        return candidates.stream().filter(c -> c.getStatus() != null && c.getStatus().isTerminal()).findFirst()
                .orElse(null);
    }

    /** 已有核保单中第一张在办（非终态）者；仓储按创建时间升序返回，故即最早一张；无则返回 null */
    private UnderwritingQueryResult firstOpen(List<UnderwritingQueryResult> candidates) {
        return candidates.stream().filter(c -> c.getStatus() == null || !c.getStatus().isTerminal()).findFirst()
                .orElse(null);
    }

    /**
     * 新建核保单并返回其ID
     * <p>
     * 🔴 <b>同值双填</b>：{@code policyId} 与 {@code insuranceId} 同时填投保单号。前者是存量语义
     * （Kafka 分区键 {@code underwriting-decided} 固定取 {@code policyId}，取值不得改变），
     * 后者是 g02-04 的投保单号正名。用户裁示「加新字段不动旧字段」即此意。
     * </p>
     * <p>
     * 🔴 <b>案件号由调用方传入</b>（确定性派生，见 {@link UnderwritingId#forAutoDecide(String, String)}），
     * 本方法不再自行取雪花号——取号动作必须与幂等键绑定，否则同键两次调用会得到两个案件号，
     * 幂等就退化为「靠反查」，而反查在读模型滞后时必然落空。
     * </p>
     */
    private String createUnderwriting(UnderwritingId underwritingId, AutoDecideRequest request) {
        CreateUnderwritingCommand command = new CreateUnderwritingCommand(underwritingId,
                PolicyId.of(request.insuranceId().value()), request.customerId(), request.amount(),
                request.underwritingType(), request.operatorId(), request.tenantId(), request.productCode(),
                underwritingNoGenerator.generateUnderwritingNo(request.tenantId()), request.insuranceId());
        commandGateway.sendAndWait(command);
        log.info("[自动决策] 无既有核保单，新建: insuranceId={}, underwritingId={}",
                request.insuranceId(), underwritingId.value());
        return underwritingId.value();
    }

    /**
     * 提交被保人粗粒度风险要素
     * <p>
     * 🔴 <b>要素全空时仍提交空容器</b>：不提交会让聚合的 {@code underwritingInput} 保持 null，
     * 而决策处理器直接取 {@code this.underwritingInput.aggregateRiskScore()}——四步路径正是靠
     * 「总是先提交输入」规避该空指针。此处保持同一顺序契约（粗粒度端点的承诺是「输入 + 决策」两步俱全）。
     * </p>
     */
    private void submitInput(String underwritingId, AutoDecideRequest request) {
        UnderwritingInput input = UnderwritingInput.builder().insuredRiskFactors(request.riskFactors()).build();
        commandGateway.sendAndWait(new SubmitUnderwritingInputCommand(new UnderwritingId(underwritingId), input,
                request.operatorId(), request.tenantId()));
    }

    /**
     * 触发核保决策（经决策编排器：产品接入规则引擎时走规则集链路，否则内置评分路径）
     */
    private UnderwritingDecidedEvent decide(String underwritingId, AutoDecideRequest request) {
        DecideUnderwritingCommand command = new DecideUnderwritingCommand(new UnderwritingId(underwritingId),
                AUTO_AUDIT_TYPE, request.operatorId(), request.tenantId(), null, null, null);
        return underwritingDecisionOrchestrator.decide(command, request.productCode());
    }
}
