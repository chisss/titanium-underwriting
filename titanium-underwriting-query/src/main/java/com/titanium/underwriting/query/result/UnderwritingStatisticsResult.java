package com.titanium.underwriting.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 核保统计查询结果 DTO（CQRS 读侧）
 * <p>
 * 基于读模型 {@code t_underwriting_view} 聚合统计而来，用于核保管理报表与业务分析。
 * </p>
 */
@Data
public class UnderwritingStatisticsResult {

    /** 统计开始时间 */
    private LocalDateTime startTime;

    /** 统计结束时间 */
    private LocalDateTime endTime;

    /** 租户ID */
    private String        tenantId;

    /** 总核保件数 */
    private Long          totalCount;

    /** 标准承保件数 */
    private Long          standardCount;

    /** 加费承保件数 */
    private Long          ratedCount;

    /** 延期承保件数 */
    private Long          postponedCount;

    /** 拒保件数 */
    private Long          declinedCount;

    /** 待处理件数 */
    private Long          pendingCount;

    /** 总核保金额 */
    private BigDecimal    totalAmount;

    /** 总通过率（标准 + 加费 / 总数） */
    private BigDecimal    overallPassRate;

    /** 统计生成时间 */
    private LocalDateTime generatedAt;
}
