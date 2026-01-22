package com.titanium.underwriting.query.query;

import java.time.LocalDateTime;

/**
 * 核保统计查询 用于获取核保业务的统计数据，支持管理报表和业务分析
 */
public record FindUnderwritingStatisticsQuery(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
}
