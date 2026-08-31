package com.titanium.underwriting.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.common.domain.BaseAggregate;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.common.constant.UnderwritingConstants;
import com.titanium.underwriting.common.enums.MaintenanceRiskClassification;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.exception.UnderwritingStatusException;
import com.titanium.underwriting.exception.UnderwritingValidationException;
import com.titanium.underwriting.service.MaintenanceUnderwritingCommandValidator;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.ExtraPremium;
import com.titanium.underwriting.valueobject.MaintenanceRiskFieldChange;
import com.titanium.underwriting.valueobject.MaintenanceUnderwritingConclusion;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Underwriting Aggregate Root
 * <p>
 * 继承 {@link BaseAggregate}，复用租户ID、创建时间、更新时间。原 createdAt/updatedAt 统一为基类
 * createTime/updateTime；createdBy/updatedBy 为核保域操作人字段，保留。
 * </p>
 */
@Aggregate
@SuperBuilder(builderMethodName = "builder")
@Getter
public class Underwriting extends BaseAggregate {
    /** 自动核保金额上限：超过该金额且无险种输入时转人工复核（回退规则） */
    private static final BigDecimal AUTO_APPROVE_AMOUNT_LIMIT = BigDecimal.valueOf(100000);

    /** 次标准体风险评分基准（评分超出该基准的部分折算加费幅度，与 UnderwritingInput 阈值一致） */
    private static final int SUB_STANDARD_SCORE_BASE = 30;

    /** 每 1 分风险评分折算的加费率（超出基准部分，如 0.02 表示每分加费 2%） */
    private static final double EXTRA_PREMIUM_RATE_PER_SCORE = 0.02d;

    /** 保全核保规则与模型版本随结论冻结，升级必须新增版本而非覆盖历史。 */
    private static final String MAINTENANCE_RULE_VERSION = "maintenance-underwriting-rules/1.1.0";
    private static final String MAINTENANCE_MODEL_VERSION = "deterministic-change-classifier/1.1.0";

    /** 保全核保命令参数校验器（独立校验器，红线 22） */
    private static final MaintenanceUnderwritingCommandValidator MAINTENANCE_COMMAND_VALIDATOR =
            new MaintenanceUnderwritingCommandValidator();

    @AggregateIdentifier
    private UnderwritingId                      underwritingId;
    private PolicyId                            policyId;
    private CustomerId                          customerId;
    private UnderwritingAmount                  amount;
    private UnderwritingEnum.UnderwritingType   underwritingType;
    private UnderwritingEnum.UnderwritingStatus status;
    /** 险种专属核保输入容器（健康告知/体检/职业/车辆风险） */
    private UnderwritingInput                   underwritingInput;
    /** 风险等级（标准体/次标准体/高风险体/不可保体），由核保决策产出 */
    private UnderwritingEnum.RiskLevel          riskLevel;
    /** 核保结论（接受/修改条件/拒绝/延期），由核保决策产出 */
    private UnderwritingEnum.ConclusionType     conclusionType;
    /** 加费明细（次标准体修改条件承保时产出，标准体/拒保为 null） */
    private ExtraPremium                        extraPremium;
    private String                              rejectReason;
    private String                              reviewComments;
    private String                              createdBy;
    private String                              updatedBy;
    /** 险种编码（UW-4：产品核保配置化，供 application 层按产品查询配置阈值） */
    private String                              productCode;
    private String                              maintenanceId;
    private Long                                maintenancePolicyBaselineVersion;
    private String                              maintenanceItemCode;
    private String                              maintenanceIdempotencyKey;
    private String                              maintenancePayloadHash;
    private MaintenanceUnderwritingConclusion   maintenanceConclusion;
    private List<String>                        maintenanceAdditionalConditions;
    private String                              maintenanceSummary;
    private LocalDateTime                       maintenanceCompletedAt;
    private LocalDateTime                       maintenanceAssessedAt;
    private String                              maintenanceAssessedBy;

    // Command Handlers
    @CommandHandler
    public Underwriting(CreateUnderwritingCommand command) {
        // Validate command
        validateCreateCommand(command);

        // Publish event
        AggregateLifecycle.apply(new UnderwritingCreatedEvent(command.underwritingId(), command.policyId(),
                command.customerId(), command.amount(), command.underwritingType(), LocalDateTime.now(),
                command.createdBy(), command.tenantId(), command.productCode()));
    }

    /** 保全核保使用独立输入模型，并以确定性聚合标识保证远程重试幂等。 */
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public MaintenanceUnderwritingAssessedEvent handle(AssessMaintenanceUnderwritingCommand command) {
        MAINTENANCE_COMMAND_VALIDATOR.validate(command);
        if (this.underwritingId != null) {
            if (!Objects.equals(this.maintenancePayloadHash, command.payloadHash())
                    || !Objects.equals(this.maintenanceIdempotencyKey, command.idempotencyKey())) {
                throw new UnderwritingValidationException(
                        UnderwritingErrorCode.IDEMPOTENCY_PAYLOAD_MISMATCH,
                        "AssessMaintenanceUnderwritingCommand", "idempotencyKey");
            }
            return currentMaintenanceAssessment();
        }

        MaintenanceAssessment assessment = assessMaintenanceRisk(command);
        LocalDateTime assessedAt = LocalDateTime.now();
        LocalDateTime completedAt = assessment.conclusion().completed() ? assessedAt : null;
        MaintenanceUnderwritingAssessedEvent event = new MaintenanceUnderwritingAssessedEvent(
                command.underwritingId(), command.tenantId(), command.maintenanceId(), command.policyId(),
                command.policyBaselineVersion(), command.itemCode(), command.idempotencyKey(), command.payloadHash(),
                MAINTENANCE_RULE_VERSION, MAINTENANCE_MODEL_VERSION, assessment.conclusion(),
                assessment.additionalConditions(), assessment.summary(), completedAt, assessedAt,
                command.requestedBy());
        AggregateLifecycle.apply(event);
        return event;
    }

    @CommandHandler
    public UnderwritingStatusChangedEvent handle(UnderwriteCommand command) {
        // Validate command
        validateUnderwriteCommand(command);

        // Determine new status based on business rules
        UnderwritingEnum.UnderwritingStatus newStatus = determineUnderwritingStatus(command);
        UnderwritingEnum.UnderwritingStatus oldStatus = this.status;

        // Publish status changed event
        UnderwritingStatusChangedEvent event = new UnderwritingStatusChangedEvent(command.underwritingId(), oldStatus,
                newStatus, command.reason(), LocalDateTime.now(), command.processedBy(), command.tenantId());
        AggregateLifecycle.apply(event);
        return event;
    }

    private UnderwritingEnum.UnderwritingStatus determineUnderwritingStatus(UnderwriteCommand command) {
        // 优先基于已提交险种专属输入评估的风险等级判定（充血模型），无输入时回退金额规则
        if (this.underwritingInput != null && this.underwritingInput.hasAnyInput()) {
            UnderwritingEnum.RiskLevel assessedLevel = this.underwritingInput.assessRiskLevel();
            return mapRiskLevelToStatus(assessedLevel);
        }
        // 回退规则：金额超过自动核保阈值需转人工复核
        // TODO 规则引擎接入：金额阈值应改由 titanium-rule-engine 按险种/租户配置
        if (command.amount().amount().compareTo(AUTO_APPROVE_AMOUNT_LIMIT) > 0) {
            return UnderwritingEnum.UnderwritingStatus.REVIEW;
        }
        return UnderwritingEnum.UnderwritingStatus.APPROVED;
    }

    /**
     * 提交险种专属核保输入
     * <p>
     * 接收健康告知/体检/职业/车辆风险等输入，供后续核保决策评估风险等级。
     * </p>
     */
    @CommandHandler
    public UnderwritingInputSubmittedEvent handle(SubmitUnderwritingInputCommand command) {
        validateSubmitInputCommand(command);

        UnderwritingInputSubmittedEvent event = new UnderwritingInputSubmittedEvent(command.underwritingId(),
                command.underwritingInput(), LocalDateTime.now(), command.submittedBy(), command.tenantId());
        AggregateLifecycle.apply(event);
        return event;
    }

    /**
     * 核保决策
     * <p>
     * 基于已提交的险种专属输入评估风险等级，并映射为核保结论与核保状态，
     * 内聚核保决策逻辑（充血模型），替代原"金额硬编码"规则。
     * </p>
     */
    @CommandHandler
    public UnderwritingDecidedEvent handle(DecideUnderwritingCommand command) {
        validateDecideCommand(command);

        // 基于险种专属输入评估风险等级（充血模型）
        int riskScore = this.underwritingInput.aggregateRiskScore();
        UnderwritingEnum.RiskLevel assessedRiskLevel = this.underwritingInput.assessRiskLevel();
        // 风险等级映射核保结论
        UnderwritingEnum.ConclusionType conclusion = deriveConclusion(assessedRiskLevel);
        // 核保结论映射核保状态
        UnderwritingEnum.UnderwritingStatus oldStatus = this.status;
        UnderwritingEnum.UnderwritingStatus newStatus = mapConclusionToStatus(conclusion);
        // 次标准体修改条件承保：产出结构化加费明细，供 billing 计算实收保费
        // surchargeAcceptable 来自产品核保配置（null 时默认允许加费，与存量行为一致）
        boolean canSurcharge = command.surchargeAcceptable() == null || command.surchargeAcceptable();
        ExtraPremium derivedExtraPremium = canSurcharge ? deriveExtraPremium(conclusion, riskScore) : null;

        UnderwritingDecidedEvent event = new UnderwritingDecidedEvent(command.underwritingId(), this.policyId,
                assessedRiskLevel, conclusion,
                command.auditType(), oldStatus, newStatus, riskScore, derivedExtraPremium, LocalDateTime.now(),
                command.decidedBy(), command.tenantId());
        AggregateLifecycle.apply(event);
        return event;
    }

    /**
     * 派生加费明细（充血模型）：仅次标准体「修改条件承保」（MODIFY）产出加费，其余结论无加费返回 null。
     * <p>
     * 加费率按风险评分超出标准体阈值的幅度线性折算——评分越高加费越多，封顶 100%。这是核保域内聚的
     * 加费决策规则（替代原先仅 RATED 状态码），产出的 {@link ExtraPremium} 随决策事件透传 billing 域。
     * </p>
     *
     * @param conclusion 核保结论
     * @param riskScore 综合风险评分
     * @return 加费明细；非 MODIFY 结论返回 null
     */
    private ExtraPremium deriveExtraPremium(UnderwritingEnum.ConclusionType conclusion, int riskScore) {
        if (conclusion != UnderwritingEnum.ConclusionType.MODIFY) {
            return null;
        }
        // 次标准体评分区间 [30,60)，超出标准体阈值 30 的部分每 1 分折算 2% 加费，封顶 100%
        int excess = Math.max(0, riskScore - SUB_STANDARD_SCORE_BASE);
        double ratio = Math.min(1.0d, excess * EXTRA_PREMIUM_RATE_PER_SCORE);
        return ExtraPremium.ofRatio(BigDecimal.valueOf(ratio), null,
                String.format(UnderwritingConstants.EXTRA_PREMIUM_REASON_TEMPLATE, riskScore));
    }

    /**
     * 风险等级映射核保结论（充血模型）
     * <p>
     * 标准体→接受承保；次标准体→修改条件（加费/除外）承保；高风险体→延期；不可保体→拒保。
     * </p>
     *
     * @param level 风险等级
     * @return 核保结论
     */
    private UnderwritingEnum.ConclusionType deriveConclusion(UnderwritingEnum.RiskLevel level) {
        return switch (level) {
            case STANDARD -> UnderwritingEnum.ConclusionType.ACCEPT;
            case SUB_STANDARD -> UnderwritingEnum.ConclusionType.MODIFY;
            case HIGH_RISK -> UnderwritingEnum.ConclusionType.POSTPONE;
            case UNINSURABLE -> UnderwritingEnum.ConclusionType.REJECT;
        };
    }

    /**
     * 核保结论映射核保状态（充血模型）
     *
     * @param conclusion 核保结论
     * @return 核保状态
     */
    private UnderwritingEnum.UnderwritingStatus mapConclusionToStatus(UnderwritingEnum.ConclusionType conclusion) {
        return switch (conclusion) {
            case ACCEPT -> UnderwritingEnum.UnderwritingStatus.STANDARD;
            case MODIFY -> UnderwritingEnum.UnderwritingStatus.RATED;
            case POSTPONE -> UnderwritingEnum.UnderwritingStatus.POSTPONED;
            case REJECT -> UnderwritingEnum.UnderwritingStatus.DECLINED;
        };
    }

    /**
     * 风险等级映射核保状态（自动核保回退路径，充血模型）
     *
     * @param level 风险等级
     * @return 核保状态
     */
    private UnderwritingEnum.UnderwritingStatus mapRiskLevelToStatus(UnderwritingEnum.RiskLevel level) {
        return mapConclusionToStatus(deriveConclusion(level));
    }

    @CommandHandler
    public void handle(ManualReviewCommand command) {
        // Validate command
        validateManualReviewCommand(command);

        // Change status to manual review
        UnderwritingEnum.UnderwritingStatus oldStatus = this.status;
        UnderwritingEnum.UnderwritingStatus newStatus = UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW;

        // Publish status changed event
        AggregateLifecycle.apply(new UnderwritingStatusChangedEvent(command.underwritingId(), oldStatus, newStatus,
                command.reviewComments(), LocalDateTime.now(), command.reviewedBy(), command.tenantId()));
    }

    // Event Sourcing Handlers
    @EventSourcingHandler
    public void on(UnderwritingCreatedEvent event) {
        this.underwritingId = event.underwritingId();
        this.policyId = event.policyId();
        this.customerId = event.customerId();
        this.amount = event.amount();
        this.underwritingType = event.underwritingType();
        this.status = UnderwritingEnum.UnderwritingStatus.PENDING;
        this.tenantId = event.tenantId();
        this.createTime = event.createdAt();
        this.createdBy = event.createdBy();
        this.updateTime = event.createdAt();
        this.updatedBy = event.createdBy();
        this.productCode = event.productCode();
    }

    @EventSourcingHandler
    public void on(UnderwritingStatusChangedEvent event) {
        this.status = event.newStatus();
        if (UnderwritingEnum.UnderwritingStatus.REJECTED.equals(event.newStatus())) {
            this.rejectReason = event.reason();
        }
        if (UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW.equals(event.newStatus())) {
            this.reviewComments = event.reason();
        }
        this.updateTime = event.changedAt();
        this.updatedBy = event.changedBy();
    }

    @EventSourcingHandler
    public void on(UnderwritingInputSubmittedEvent event) {
        this.underwritingInput = event.underwritingInput();
        this.updateTime = event.submittedAt();
        this.updatedBy = event.submittedBy();
    }

    @EventSourcingHandler
    public void on(UnderwritingDecidedEvent event) {
        this.riskLevel = event.riskLevel();
        this.conclusionType = event.conclusionType();
        this.extraPremium = event.extraPremium();
        this.status = event.newStatus();
        this.updateTime = event.decidedAt();
        this.updatedBy = event.decidedBy();
    }

    @EventSourcingHandler
    public void on(MaintenanceUnderwritingAssessedEvent event) {
        this.underwritingId = event.underwritingId();
        this.tenantId = event.tenantId();
        this.policyId = PolicyId.of(event.policyId());
        this.underwritingType = UnderwritingEnum.UnderwritingType.ENDORSEMENT;
        this.status = mapMaintenanceStatus(event.conclusion());
        this.maintenanceId = event.maintenanceId();
        this.maintenancePolicyBaselineVersion = event.policyBaselineVersion();
        this.maintenanceItemCode = event.itemCode();
        this.maintenanceIdempotencyKey = event.idempotencyKey();
        this.maintenancePayloadHash = event.payloadHash();
        this.maintenanceConclusion = event.conclusion();
        this.maintenanceAdditionalConditions = event.additionalConditions();
        this.maintenanceSummary = event.summary();
        this.maintenanceCompletedAt = event.completedAt();
        this.maintenanceAssessedAt = event.assessedAt();
        this.maintenanceAssessedBy = event.assessedBy();
        this.createTime = event.assessedAt();
        this.updateTime = event.assessedAt();
        this.createdBy = event.assessedBy();
        this.updatedBy = event.assessedBy();
    }

    private MaintenanceAssessment assessMaintenanceRisk(AssessMaintenanceUnderwritingCommand command) {
        if (!command.configurationRequiresUnderwriting() && command.riskFieldChanges().isEmpty()) {
            return new MaintenanceAssessment(
                    MaintenanceUnderwritingConclusion.NOT_REQUIRED, List.of(),
                    UnderwritingConstants.MAINTENANCE_SUMMARY_NOT_REQUIRED);
        }
        if (!command.configurationRequiresUnderwriting()) {
            return new MaintenanceAssessment(
                    MaintenanceUnderwritingConclusion.MANUAL_REVIEW, List.of(),
                    UnderwritingConstants.MAINTENANCE_SUMMARY_SKIPPED_WITH_CHANGES);
        }
        boolean rejected = false;
        boolean manual = false;
        List<String> conditions = new ArrayList<>();
        for (MaintenanceRiskFieldChange change : command.riskFieldChanges()) {
            MaintenanceRiskClassification classification =
                    MaintenanceRiskClassification.of(change.changeTypeCode());
            switch (classification.getVerdict()) {
                case REJECT -> rejected = true;
                case MANUAL_REVIEW -> manual = true;
                case CONDITIONAL -> conditions.add(UnderwritingConstants.MAINTENANCE_CONDITION_PREFIX
                        + change.fieldCode());
                case ACCEPT -> {
                    // 自动接受类变更：无附加动作
                }
            }
        }
        if (rejected) {
            return new MaintenanceAssessment(
                    MaintenanceUnderwritingConclusion.REJECTED, List.of(),
                    UnderwritingConstants.MAINTENANCE_SUMMARY_REJECTED);
        }
        if (manual || command.riskFieldChanges().isEmpty()) {
            return new MaintenanceAssessment(
                    MaintenanceUnderwritingConclusion.MANUAL_REVIEW, List.of(),
                    UnderwritingConstants.MAINTENANCE_SUMMARY_MANUAL_REVIEW);
        }
        if (!conditions.isEmpty()) {
            return new MaintenanceAssessment(
                    MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED, conditions,
                    UnderwritingConstants.MAINTENANCE_SUMMARY_CONDITIONAL_APPROVED);
        }
        return new MaintenanceAssessment(
                MaintenanceUnderwritingConclusion.APPROVED, List.of(),
                UnderwritingConstants.MAINTENANCE_SUMMARY_APPROVED);
    }

    private MaintenanceUnderwritingAssessedEvent currentMaintenanceAssessment() {
        return new MaintenanceUnderwritingAssessedEvent(
                underwritingId, tenantId, maintenanceId, policyId.value(), maintenancePolicyBaselineVersion,
                maintenanceItemCode, maintenanceIdempotencyKey, maintenancePayloadHash,
                MAINTENANCE_RULE_VERSION, MAINTENANCE_MODEL_VERSION, maintenanceConclusion,
                maintenanceAdditionalConditions, maintenanceSummary, maintenanceCompletedAt,
                maintenanceAssessedAt, maintenanceAssessedBy);
    }

    private UnderwritingEnum.UnderwritingStatus mapMaintenanceStatus(
            MaintenanceUnderwritingConclusion conclusion) {
        return switch (conclusion) {
            case NOT_REQUIRED, APPROVED -> UnderwritingEnum.UnderwritingStatus.APPROVED;
            case CONDITIONAL_APPROVED -> UnderwritingEnum.UnderwritingStatus.RATED;
            case MANUAL_REVIEW -> UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW;
            case REJECTED -> UnderwritingEnum.UnderwritingStatus.DECLINED;
        };
    }

    private record MaintenanceAssessment(
            MaintenanceUnderwritingConclusion conclusion,
            List<String> additionalConditions,
            String summary) {
    }

    // Business Logic
    private void validateCreateCommand(CreateUnderwritingCommand command) {
        final String commandName = "CreateUnderwritingCommand";
        if (command.underwritingId() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_ID_REQUIRED,
                    commandName, "underwritingId");
        }
        if (command.policyId() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.POLICY_ID_REQUIRED,
                    commandName, "policyId");
        }
        if (command.customerId() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.CUSTOMER_ID_REQUIRED,
                    commandName, "customerId");
        }
        if (command.amount() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_AMOUNT_REQUIRED,
                    commandName, "amount");
        }
        if (command.underwritingType() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_TYPE_REQUIRED,
                    commandName, "underwritingType");
        }
        if (command.createdBy() == null || command.createdBy().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.CREATED_BY_REQUIRED,
                    commandName, "createdBy");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.TENANT_ID_REQUIRED,
                    commandName, "tenantId");
        }
    }

    private void validateUnderwriteCommand(UnderwriteCommand command) {
        final String commandName = "UnderwriteCommand";
        if (!this.underwritingId.equals(command.underwritingId())) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_ID_MISMATCH,
                    commandName, "underwritingId");
        }
        if (command.processedBy() == null || command.processedBy().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.PROCESSED_BY_REQUIRED,
                    commandName, "processedBy");
        }
        if (command.reason() == null || command.reason().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.REASON_REQUIRED,
                    commandName, "reason");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.TENANT_ID_REQUIRED,
                    commandName, "tenantId");
        }
    }

    private void validateManualReviewCommand(ManualReviewCommand command) {
        final String commandName = "ManualReviewCommand";
        if (!this.underwritingId.equals(command.underwritingId())) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_ID_MISMATCH,
                    commandName, "underwritingId");
        }
        if (command.reviewedBy() == null || command.reviewedBy().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.REVIEWED_BY_REQUIRED,
                    commandName, "reviewedBy");
        }
        if (command.reviewComments() == null || command.reviewComments().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.REVIEW_COMMENTS_REQUIRED,
                    commandName, "reviewComments");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.TENANT_ID_REQUIRED,
                    commandName, "tenantId");
        }
    }

    private void validateSubmitInputCommand(SubmitUnderwritingInputCommand command) {
        final String commandName = "SubmitUnderwritingInputCommand";
        if (!this.underwritingId.equals(command.underwritingId())) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_ID_MISMATCH,
                    commandName, "underwritingId");
        }
        if (command.underwritingInput() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_INPUT_REQUIRED,
                    commandName, "underwritingInput");
        }
        if (command.submittedBy() == null || command.submittedBy().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.SUBMITTED_BY_REQUIRED,
                    commandName, "submittedBy");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.TENANT_ID_REQUIRED,
                    commandName, "tenantId");
        }
    }

    private void validateDecideCommand(DecideUnderwritingCommand command) {
        final String commandName = "DecideUnderwritingCommand";
        if (!this.underwritingId.equals(command.underwritingId())) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_ID_MISMATCH,
                    commandName, "underwritingId");
        }
        if (command.auditType() == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.AUDIT_TYPE_REQUIRED,
                    commandName, "auditType");
        }
        if (command.decidedBy() == null || command.decidedBy().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.DECIDED_BY_REQUIRED,
                    commandName, "decidedBy");
        }
        if (command.tenantId() == null || command.tenantId().trim().isEmpty()) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.TENANT_ID_REQUIRED,
                    commandName, "tenantId");
        }
        // 决策前必须已提交险种专属输入，否则违反核保业务规则
        if (this.underwritingInput == null) {
            throw new UnderwritingStatusException(UnderwritingErrorCode.UNDERWRITING_DECISION_INPUT_MISSING,
                    this.underwritingId.toString(),
                    this.status == null ? "UNKNOWN" : this.status.getCode(),
                    UnderwritingEnum.UnderwritingStatus.APPROVED.getCode(),
                    "核保决策前必须先提交险种专属核保输入");
        }
    }

    /**
     * 默认构造函数
     * <p>
     * 受保护的默认构造函数，用于 Axon Framework 的反序列化和实例化
     */
    protected Underwriting() {
        // Required by Axon Framework
    }


}
