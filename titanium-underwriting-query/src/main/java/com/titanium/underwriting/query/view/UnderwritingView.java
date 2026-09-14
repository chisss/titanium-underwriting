package com.titanium.underwriting.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;
import com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 核保读模型实体（CQRS Projection）
 * <p>
 * 对应读模型表 {@code t_underwriting_view}，与写侧事件溯源存储物理隔离。 由
 * {@link com.titanium.underwriting.query.handler.projection.UnderwritingProjectionEventHandler}
 * 订阅核保域领域事件投影而来，替代原「重建写侧聚合根来查询」的读写混用反模式。
 * </p>
 * <p>
 * 继承 {@link BaseView}，复用租户ID、创建/更新时间、乐观锁版本等读模型公共字段； 读模型时间为「投影时间」，由投影处理器显式赋值。
 * </p>
 */
@Entity
@Table(name = "t_underwriting_view", indexes = {
        @Index(name = "idx_uw_view_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_uw_view_policy_id", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_uw_view_customer_id", columnList = "customer_id, tenant_id"),
        @Index(name = "idx_uw_view_status", columnList = "status, tenant_id"),
        @Index(name = "idx_uw_view_risk_level", columnList = "risk_level, tenant_id"),
        @Index(name = "idx_uw_view_underwriter", columnList = "underwriter_id, tenant_id"),
        @Index(name = "idx_uw_view_audit_type", columnList = "audit_type, tenant_id") })
@Getter
@Setter
public class UnderwritingView extends BaseView {

    /** 核保ID（聚合根ID，读模型主键） */
    @Id
    @Column(name = "underwriting_id", length = 50, nullable = false)
    private String                              underwritingId;

    /** 核保案号（UW 前缀业务号，创建时由应用层发号生成，随创建事件投影） */
    @Column(name = "case_no", length = 50)
    private String                              caseNo;

    /** 保单ID */
    @Column(name = "policy_id", length = 50, nullable = false)
    private String                              policyId;

    /** 客户ID */
    @Column(name = "customer_id", length = 50, nullable = false)
    private String                              customerId;

    /** 核保金额 */
    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal                          amount;

    /** 核保类型（新单/续保/保全/复效） */
    @Enumerated(EnumType.STRING)
    @Column(name = "underwriting_type", length = 50)
    private UnderwritingEnum.UnderwritingType   underwritingType;

    /** 核保状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private UnderwritingEnum.UnderwritingStatus status;

    /** 拒保原因 */
    @Column(name = "reject_reason", length = 1000)
    private String                              rejectReason;

    /** 审核意见 */
    @Column(name = "review_comments", length = 2000)
    private String                              reviewComments;

    /** 除外原因（N2：除外承保时规则引擎给出的除外说明） */
    @Column(name = "exclusion_reason", length = 2000)
    private String                              exclusionReason;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 50)
    private UnderwritingEnum.RiskLevel          riskLevel;

    /** 核保结论类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion_type", length = 50)
    private UnderwritingEnum.ConclusionType     conclusionType;

    /** 核保方式（自动/人工/混合） */
    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", length = 50)
    private UnderwritingEnum.AuditType          auditType;

    /** 核保员ID */
    @Column(name = "underwriter_id", length = 50)
    private String                              underwriterId;

    /** 综合风险评分 */
    @Column(name = "risk_score")
    private Integer                             riskScore;

    /** 加费类型（UW-3：结构化加费，次标准体修改条件承保时填充） */
    @Column(name = "extra_premium_type", length = 50)
    private String                              extraPremiumType;

    /** 加费率（比例加费时用，如 0.30 表示加费30%） */
    @Column(name = "extra_premium_ratio", precision = 10, scale = 4)
    private BigDecimal                          extraPremiumRatio;

    /** 固定加费额（固定额加费时用） */
    @Column(name = "extra_premium_fixed_amount", precision = 18, scale = 2)
    private BigDecimal                          extraPremiumFixedAmount;

    /** 加费原因（dev-505：规则引擎加费结论的原因说明，随事件落库供展示/审计） */
    @Column(name = "extra_premium_reason", length = 500)
    private String                              extraPremiumReason;

    /** 是否已提交险种专属核保输入（核保决策的前提条件，工作台据此筛选待决策件） */
    @Column(name = "input_submitted")
    private Boolean                             inputSubmitted;

    /** 提交输入时的综合风险评分（决策前即可预览风险，与决策事件的 riskScore 同源） */
    @Column(name = "input_risk_score")
    private Integer                             inputRiskScore;

    /** 保全单ID（保全核保路径特有） */
    @Column(name = "maintenance_id", length = 50)
    private String                              maintenanceId;

    /** 保全事项编码 */
    @Column(name = "maintenance_item_code", length = 50)
    private String                              maintenanceItemCode;

    /** 保全核保结论（语义独立于新单核保结论类型） */
    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_conclusion", length = 50)
    private MaintenanceUnderwritingConclusion   maintenanceConclusion;

    /** 保全核保结论摘要 */
    @Column(name = "maintenance_summary", length = 500)
    private String                              maintenanceSummary;

    /** 保全核保附加条件（JSON 数组文本，空表示无条件） */
    @Column(name = "maintenance_additional_conditions_json", columnDefinition = "TEXT")
    private String                              maintenanceAdditionalConditionsJson;

    /** 保全核保完成时间（转人工复核时为空，与聚合 completedAt 口径一致） */
    @Column(name = "maintenance_completed_at")
    private LocalDateTime                       maintenanceCompletedAt;

    /** 业务创建人 */
    @Column(name = "created_by", length = 50)
    private String                              createdBy;

    /** 业务更新人 */
    @Column(name = "updated_by", length = 50)
    private String                              updatedBy;

    /** 业务创建时间（来源事件） */
    @Column(name = "created_at")
    private LocalDateTime                       createdAt;
}
