package com.titanium.underwriting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 核保统计DTO - 用于API层统计数据传输
 * 基于保险业务全生命周期核保承保阶段的统计分析需求设计
 */
@Schema(description = "核保统计DTO")
@Data
public class UnderwritingStatisticsDTO {

    // ========== 基础统计信息 ==========
    @Schema(description = "统计开始时间")
    private LocalDateTime startTime;

    @Schema(description = "统计结束时间")
    private LocalDateTime endTime;

    @Schema(description = "租户ID")
    private String tenantId;

    // ========== 核保数量统计 ==========
    @Schema(description = "总核保件数", example = "1000")
    private Long totalCount;

    @Schema(description = "标准承保件数", example = "800")
    private Long standardCount;

    @Schema(description = "加费承保件数", example = "150")
    private Long surchargeCount;

    @Schema(description = "除外承保件数", example = "30")
    private Long exclusionCount;

    @Schema(description = "延期承保件数", example = "15")
    private Long postponeCount;

    @Schema(description = "拒保件数", example = "5")
    private Long declineCount;

    // ========== 核保金额统计 ==========
    @Schema(description = "总核保金额", example = "50000000.00")
    private BigDecimal totalAmount;

    @Schema(description = "标准承保金额", example = "40000000.00")
    private BigDecimal standardAmount;

    @Schema(description = "加费承保金额", example = "7500000.00")
    private BigDecimal surchargeAmount;

    @Schema(description = "除外承保金额", example = "1500000.00")
    private BigDecimal exclusionAmount;

    @Schema(description = "延期承保金额", example = "750000.00")
    private BigDecimal postponeAmount;

    @Schema(description = "拒保金额", example = "250000.00")
    private BigDecimal declineAmount;

    // ========== 核保效率统计 ==========
    @Schema(description = "平均核保时效（小时）", example = "18.5")
    private BigDecimal averageProcessingHours;

    @Schema(description = "自动核保件数", example = "600")
    private Long autoUnderwritingCount;

    @Schema(description = "人工核保件数", example = "350")
    private Long manualUnderwritingCount;

    @Schema(description = "混合核保件数", example = "50")
    private Long hybridUnderwritingCount;

    // ========== 风险等级统计 ==========
    @Schema(description = "低风险件数", example = "700")
    private Long lowRiskCount;

    @Schema(description = "中风险件数", example = "250")
    private Long mediumRiskCount;

    @Schema(description = "高风险件数", example = "50")
    private Long highRiskCount;

    // ========== 核保通过率统计 ==========
    @Schema(description = "总通过率", example = "0.95")
    private BigDecimal overallPassRate;

    @Schema(description = "标准承保率", example = "0.80")
    private BigDecimal standardPassRate;

    @Schema(description = "加费承保率", example = "0.15")
    private BigDecimal surchargePassRate;

    @Schema(description = "除外承保率", example = "0.03")
    private BigDecimal exclusionPassRate;

    @Schema(description = "拒保率", example = "0.005")
    private BigDecimal declineRate;

    // ========== 系统字段 ==========
    @Schema(description = "统计生成时间")
    private LocalDateTime generatedAt;

    @Schema(description = "统计生成人")
    private String generatedBy;
}
