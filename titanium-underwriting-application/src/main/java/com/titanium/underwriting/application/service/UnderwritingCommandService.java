package com.titanium.underwriting.application.service;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.titanium.underwriting.port.ProductUnderwritingConfigPort;
import com.titanium.underwriting.port.ProductUnderwritingConfigPort.ProductUnderwritingConfig;

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

    private final CommandGateway                 commandGateway;
    private final ProductUnderwritingConfigPort  productUnderwritingConfigPort;

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
     * 触发核保决策
     * <p>
     * 基于已提交的结构化输入与核保金额，由核保聚合根内聚决策产出结论
     * （ACCEPT/MODIFY/REJECT/POSTPONE）与风险等级。若命令未携带 {@code surchargeAcceptable}，
     * 按默认配置（允许加费）决策。
     * </p>
     *
     * @param command 核保决策命令
     */
    public UnderwritingDecidedEvent decide(DecideUnderwritingCommand command) {
        return commandGateway.sendAndWait(command);
    }

    /**
     * 触发核保决策（UW-4：产品核保配置化）
     * <p>
     * application 编排：先经 {@link ProductUnderwritingConfigPort} 按险种编码读取产品核保配置
     * （是否接受加费等），据此充实决策命令的 {@code surchargeAcceptable}，再派发给聚合根决策。
     * 配置计算职责在 product 域配置、underwriting 域决策——用配置替代聚合内硬编码加费策略。
     * {@code productCode} 为空时走默认配置（允许加费）。
     * </p>
     *
     * @param command     核保决策命令（web 层构造，surchargeAcceptable 待充实）
     * @param productCode 险种编码（可为空）
     */
    public UnderwritingDecidedEvent decide(DecideUnderwritingCommand command, String productCode) {
        ProductUnderwritingConfig config = productUnderwritingConfigPort.fetchConfig(productCode, command.tenantId());
        log.info("[核保决策] 应用产品核保配置: productCode={}, surchargeAcceptable={}", productCode,
                config.surchargeAcceptable());
        DecideUnderwritingCommand enriched = new DecideUnderwritingCommand(command.underwritingId(),
                command.auditType(), command.decidedBy(), command.tenantId(), config.surchargeAcceptable());
        return commandGateway.sendAndWait(enriched);
    }
}
