package com.titanium.underwriting.api.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 核保统计远程响应契约（Feign 出参）
 * <p>
 * 面向管理后台等跨服务消费者的对外传输契约，承载核保维度聚合统计：总核保件数、各决策类型件数、
 * 待处理件数、总核保金额及总通过率。由 web 层经 MapStruct 从读侧 {@code UnderwritingStatisticsResult}
 * 转换而来，作为稳定协议隔离读模型内部结构（统计时间窗、租户、生成时间等内部字段不外泄）。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingStatisticsResponse {

    /** 总核保件数 */
    private Long totalCount;

    /** 标准承保件数 */
    private Long standardCount;

    /** 加费承保件数 */
    private Long ratedCount;

    /** 延期承保件数 */
    private Long postponedCount;

    /** 拒保件数 */
    private Long declinedCount;

    /** 待处理件数 */
    private Long pendingCount;

    /** 总核保金额 */
    private BigDecimal totalAmount;

    /** 总通过率（标准 + 加费 / 总数） */
    private BigDecimal overallPassRate;
}
