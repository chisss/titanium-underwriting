package com.titanium.underwriting.application.service;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.underwriting.application.orchestration.UnderwritingDecisionOrchestrator;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保命令服务（写侧应用入口门面）
 * <p>
 * 作为写用例入口：入参即领域命令，仅经 {@link CommandGateway} 派发，保持门面「薄」。
 * Request/DTO → 领域命令的协议翻译已上移至 web 层（{@code UnderwritingWebMapper}），
 * application 门面不再依赖 api 契约（DTO/Request），也不做读模型到 DTO 的组装
 * （读侧对外表示由 web 层完成），彻底切断对 api 层的编译期依赖（项目规约 3.4.8 ①）。
 * </p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UnderwritingCommandService {

    private final CommandGateway                  commandGateway;
    private final UnderwritingDecisionOrchestrator underwritingDecisionOrchestrator;

    /**
     * 创建核保
     *
     * @param command 创建核保命令
     * @return 新建核保ID
     */
    public String createUnderwriting(CreateUnderwritingCommand command) {
        commandGateway.sendAndWait(command);
        return command.underwritingId().value();
    }

    /** 派发保全专用核保评估，返回聚合冻结的权威结论。 */
    public MaintenanceUnderwritingAssessedEvent assessMaintenance(
            AssessMaintenanceUnderwritingCommand command) {
        return commandGateway.sendAndWait(command);
    }

    /**
     * 执行核保
     *
     * @param command 执行核保命令
     */
    public UnderwritingStatusChangedEvent underwrite(UnderwriteCommand command) {
        return commandGateway.sendAndWait(command);
    }

    /**
     * 手动审核
     *
     * @param command 手动审核命令
     */
    public void manualReview(ManualReviewCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 提交险种专属核保输入（健康告知/体检/职业/车辆风险）
     *
     * @param command 提交核保输入命令
     */
    public UnderwritingInputSubmittedEvent submitInput(SubmitUnderwritingInputCommand command) {
        return commandGateway.sendAndWait(command);
    }

    /**
     * 触发核保决策（dev-505：经决策编排器，产品接入规则引擎时走规则集链路，否则内置评分路径）
     * <p>
     * 编排职责全部下沉 {@link UnderwritingDecisionOrchestrator}：加载聚合 → 取产品核保配置 →
     * （接入规则引擎时）装配上下文/提取特征/执行规则集/结论映射 → 派发决策命令。
     * 本门面零业务规则。
     * </p>
     *
     * @param command 核保决策命令
     */
    public UnderwritingDecidedEvent decide(DecideUnderwritingCommand command) {
        return underwritingDecisionOrchestrator.decide(command, null);
    }

    /**
     * 触发核保决策（显式险种编码入口，dev-505：产品核保配置化）
     * <p>
     * 与 {@link #decide(DecideUnderwritingCommand)} 同一编排链路；{@code productCode} 为空时
     * 回退聚合携带的险种编码（创建核保时记录）。配置计算职责在 product 域配置、
     * underwriting 域决策——用配置替代聚合内硬编码加费策略。
     * </p>
     *
     * @param command     核保决策命令（web 层构造，surchargeAcceptable 待充实）
     * @param productCode 险种编码（可为空）
     */
    public UnderwritingDecidedEvent decide(DecideUnderwritingCommand command, String productCode) {
        return underwritingDecisionOrchestrator.decide(command, productCode);
    }
}
