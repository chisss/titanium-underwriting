package com.titanium.underwriting.query.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import lombok.Data;

/**
 * 核保查询结果实体 - Query层 用于查询结果的返回数据结构 基于保险业务全生命周期核保承保阶段的关键信息设计
 * 符合DDD架构：Query层负责「聚合根→QueryResult」转换
 */
@Data
public class UnderwritingQueryResult {
    /** 核保ID */
    private String                              underwritingId;

    /** 保单ID */
    private String                              policyId;

    /** 客户ID */
    private String                              customerId;

    /** 保额 */
    private BigDecimal                          amount;

    /** 核保类型 */
    private String                              underwritingType;

    /** 核保状态 */
    private UnderwritingEnum.UnderwritingStatus status;

    /** 拒保原因 */
    private String                              rejectReason;

    /** 审核意见 */
    private String                              reviewComments;

    // ========== 核保依据相关信息 ==========
    /** 风险等级 */
    private UnderwritingEnum.RiskLevel          riskLevel;

    /** 核保结论类型 */
    private UnderwritingEnum.ConclusionType     conclusionType;

    /** 核保类型（自动/人工/混合） */
    private UnderwritingEnum.AuditType          auditType;

    /** 核保员ID */
    private String                              underwriterId;

    /** 核保员姓名 */
    private String                              underwriterName;

    // ========== 风险评估结果 ==========
    /** 风险因子 */
    private String                              riskFactors;

    /** 加费比例（如+20%） */
    private BigDecimal                          premiumSurchargeRate;

    /** 加费原因 */
    private String                              surchargeReason;

    /** 除外责任 */
    private String                              exclusions;

    /** 延期期限（月） */
    private Integer                             postponePeriodMonths;

    /** 延期原因 */
    private String                              postponeReason;

    // ========== 第三方数据相关 ==========
    /** 体检报告ID */
    private String                              medicalExamId;

    /** 体检状态 */
    private String                              medicalExamStatus;

    /** 第三方调查结果 */
    private String                              investigationResult;

    /** 同业拒保记录 */
    private String                              industryDeclineRecord;

    // ========== 核保流程相关 ==========
    /** 核保开始时间 */
    private LocalDateTime                       underwritingStartTime;

    /** 核保完成时间 */
    private LocalDateTime                       underwritingCompletedTime;

    /** 核保时效（小时） */
    private Integer                             processingHours;

    /** 是否需要复核 */
    private Boolean                             requiresReview;

    /** 复核员ID */
    private String                              reviewerId;

    /** 复核意见 */
    private String                              reviewerComments;

    // ========== 保费相关 ==========
    /** 基准保费 */
    private BigDecimal                          basePremium;

    /** 附加保费 */
    private BigDecimal                          additionalPremium;

    /** 最终保费 */
    private BigDecimal                          finalPremium;

    /** 优惠金额 */
    private BigDecimal                          discountAmount;

    // ========== 系统字段 ==========
    private LocalDateTime                       createdAt;
    private String                              createdBy;
    private LocalDateTime                       updatedAt;
    private String                              updatedBy;
    private String                              tenantId;

    /** 版本号（用于乐观锁） */
    private Long                                version;
}
