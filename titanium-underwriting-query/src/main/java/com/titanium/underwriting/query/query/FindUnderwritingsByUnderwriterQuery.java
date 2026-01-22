package com.titanium.underwriting.query.query;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

/**
 * 根据核保员查询核保信息列表
 * 支持按核保员ID和时间范围查询核保记录
 */
public record FindUnderwritingsByUnderwriterQuery(
        String underwriterId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Pageable pageable,
        String tenantId
) {
}