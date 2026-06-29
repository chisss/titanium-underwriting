package com.titanium.underwriting.web.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 核保VO - 用于Web层数据展示
 * 基于保险业务全生命周期核保承保阶段的业务需求设计
 */
@Schema(description = "核保VO")
@Data
public class UnderwritingVO {

    // ========== 基础信息 ==========
    @Schema(description = "核保ID", example = "UW202401001")
    private String underwritingId;

    @Schema(description = "保单ID", example = "POL202401001")
    private String policyId;

    @Schema(description = "客户ID", example = "CUST202401001")
    private String customerId;

    @Schema(description = "核保金额", example = "500000.00")
    private BigDecimal amount;

    @Schema(description = "核保类型", example = "NEW_BUSINESS")
    private UnderwritingEnum.UnderwritingType underwritingType;

    @Schema(description = "核保状态")
    private UnderwritingEnum.UnderwritingStatus status;

    @Schema(description = "拒绝原因")
    private String rejectReason;

    @Schema(description = "核保评论")
    private String reviewComments;

    // ========== 核保结果信息 ==========
    @Schema(description = "风险等级")
    private UnderwritingEnum.RiskLevel riskLevel;

    @Schema(description = "核保结论类型")
    private UnderwritingEnum.ConclusionType conclusionType;

    @Schema(description = "核保类型（自动/人工/混合）")
    private UnderwritingEnum.AuditType auditType;

    @Schema(description = "核保员ID")
    private String underwriterId;

    @Schema(description = "核保员姓名")
    private String underwriterName;

    // ========== 风险评估结果 ==========
    @Schema(description = "风险因子")
    private String riskFactors;

    @Schema(description = "加费比例", example = "0.20")
    private BigDecimal premiumSurchargeRate;

    @Schema(description = "加费原因")
    private String surchargeReason;

    @Schema(description = "除外责任")
    private String exclusions;

    @Schema(description = "延期期限（月）", example = "6")
    private Integer postponePeriodMonths;

    @Schema(description = "延期原因")
    private String postponeReason;

    // ========== 第三方数据相关 ==========
    @Schema(description = "体检报告ID")
    private String medicalExamId;

    @Schema(description = "体检状态")
    private UnderwritingEnum.MedicalExamStatus medicalExamStatus;

    @Schema(description = "第三方调查结果")
    private UnderwritingEnum.InvestigationResult investigationResult;

    @Schema(description = "同业拒保记录")
    private String industryDeclineRecord;

    // ========== 核保流程相关 ==========
    @Schema(description = "核保开始时间")
    private LocalDateTime underwritingStartTime;

    @Schema(description = "核保完成时间")
    private LocalDateTime underwritingCompletedTime;

    @Schema(description = "核保时效（小时）", example = "24")
    private Integer processingHours;

    @Schema(description = "是否需要复核")
    private Boolean requiresReview;

    @Schema(description = "复核员ID")
    private String reviewerId;

    @Schema(description = "复核意见")
    private String reviewerComments;

    // ========== 保费相关 ==========
    @Schema(description = "基准保费", example = "5000.00")
    private BigDecimal basePremium;

    @Schema(description = "附加保费", example = "1000.00")
    private BigDecimal additionalPremium;

    @Schema(description = "最终保费", example = "6000.00")
    private BigDecimal finalPremium;

    @Schema(description = "优惠金额", example = "200.00")
    private BigDecimal discountAmount;

    // ========== 系统字段 ==========
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "更新人")
    private String updatedBy;

    @Schema(description = "版本号（用于乐观锁）")
    private Long version;
}
