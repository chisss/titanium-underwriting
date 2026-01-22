package com.titanium.underwriting.query.query;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

/**
 * 多条件组合查询核保信息
 * 支持按多个维度组合查询核保记录，用于复杂业务场景
 */
public record FindUnderwritingsByMultipleConditionsQuery(
        UnderwritingEnum.UnderwritingStatus status,
        UnderwritingEnum.RiskLevel riskLevel,
        UnderwritingEnum.AuditType auditType,
        String underwriterId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Pageable pageable,
        String tenantId
) {
}