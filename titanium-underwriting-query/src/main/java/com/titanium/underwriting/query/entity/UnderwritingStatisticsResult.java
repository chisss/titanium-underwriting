package com.titanium.underwriting.query.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 核保统计查询结果实体 - Query层 用于返回核保业务的统计分析数据 基于保险业务全生命周期核保承保阶段的统计分析需求设计
 * 符合DDD架构：Query层负责「聚合根→StatisticsResult」转换
 */
@Data
public class UnderwritingStatisticsResult {

    // ========== 基础统计信息 ==========
    /** 统计开始时间 */
    private LocalDateTime startTime;

    /** 统计结束时间 */
    private LocalDateTime endTime;

    /** 租户ID */
    private String        tenantId;

    // ========== 核保数量统计 ==========
    /** 总核保件数 */
    private Long          totalCount;

    /** 标准承保件数 */
    private Long          standardCount;

    /** 加费承保件数 */
    private Long          surchargeCount;

    /** 除外承保件数 */
    private Long          exclusionCount;

    /** 延期承保件数 */
    private Long          postponeCount;

    /** 拒保件数 */
    private Long          declineCount;

    // ========== 核保金额统计 ==========
    /** 总核保金额 */
    private BigDecimal    totalAmount;

    /** 标准承保金额 */
    private BigDecimal    standardAmount;

    /** 加费承保金额 */
    private BigDecimal    surchargeAmount;

    /** 除外承保金额 */
    private BigDecimal    exclusionAmount;

    /** 延期承保金额 */
    private BigDecimal    postponeAmount;

    /** 拒保金额 */
    private BigDecimal    declineAmount;

    // ========== 核保效率统计 ==========
    /** 平均核保时效（小时） */
    private BigDecimal    averageProcessingHours;

    /** 自动核保件数 */
    private Long          autoUnderwritingCount;

    /** 人工核保件数 */
    private Long          manualUnderwritingCount;

    /** 混合核保件数 */
    private Long          hybridUnderwritingCount;

    // ========== 风险等级统计 ==========
    /** 低风险件数 */
    private Long          lowRiskCount;

    /** 中风险件数 */
    private Long          mediumRiskCount;

    /** 高风险件数 */
    private Long          highRiskCount;

    // ========== 核保通过率统计 ==========
    /** 总通过率 */
    private BigDecimal    overallPassRate;

    /** 标准承保率 */
    private BigDecimal    standardPassRate;

    /** 加费承保率 */
    private BigDecimal    surchargePassRate;

    /** 除外承保率 */
    private BigDecimal    exclusionPassRate;

    /** 拒保率 */
    private BigDecimal    declineRate;

    // ========== 系统字段 ==========
    /** 统计生成时间 */
    private LocalDateTime generatedAt;

    /** 统计生成人 */
    private String        generatedBy;
}
