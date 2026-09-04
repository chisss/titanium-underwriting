package com.titanium.underwriting.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import lombok.Data;

/**
 * 核保查询结果 DTO（CQRS 读侧对外契约）
 * <p>
 * 由 {@link com.titanium.underwriting.query.service.UnderwritingQueryService} 从读模型
 * {@code UnderwritingView} 组装，作为查询侧稳定返回契约，避免直接返回读模型实体泄漏持久化细节。
 * </p>
 */
@Data
public class UnderwritingQueryResult {

    /** 核保ID */
    private String                              underwritingId;

    /** 保单ID */
    private String                              policyId;

    /** 客户ID */
    private String                              customerId;

    /** 核保金额 */
    private BigDecimal                          amount;

    /** 核保类型 */
    private UnderwritingEnum.UnderwritingType   underwritingType;

    /** 核保状态 */
    private UnderwritingEnum.UnderwritingStatus status;

    /** 拒保原因 */
    private String                              rejectReason;

    /** 审核意见 */
    private String                              reviewComments;

    /** 风险等级 */
    private UnderwritingEnum.RiskLevel          riskLevel;

    /** 核保结论类型 */
    private UnderwritingEnum.ConclusionType     conclusionType;

    /** 核保方式（自动/人工/混合） */
    private UnderwritingEnum.AuditType          auditType;

    /** 核保员ID */
    private String                              underwriterId;

    /** 综合风险评分 */
    private Integer                             riskScore;

    /** 加费类型（UW-3：结构化加费） */
    private String                              extraPremiumType;

    /** 加费率（比例加费时用） */
    private BigDecimal                          extraPremiumRatio;

    /** 固定加费额（固定额加费时用） */
    private BigDecimal                          extraPremiumFixedAmount;

    /** 加费原因（规则引擎加费结论的原因说明） */
    private String                              extraPremiumReason;

    /** 业务创建时间 */
    private LocalDateTime                       createdAt;

    /** 创建人 */
    private String                              createdBy;

    /** 更新时间 */
    private LocalDateTime                       updatedAt;

    /** 更新人 */
    private String                              updatedBy;

    /** 租户ID */
    private String                              tenantId;

    /** 版本号（乐观锁） */
    private Long                                version;
}
