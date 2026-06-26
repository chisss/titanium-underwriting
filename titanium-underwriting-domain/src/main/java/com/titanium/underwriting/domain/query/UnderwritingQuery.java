package com.titanium.underwriting.domain.query;

import com.titanium.underwriting.domain.valueobject.UnderwritingId;

/**
 * 核保查询类
 * <p>
 * 用于查询核保详情
 * </p>
 */
public record UnderwritingQuery(
        UnderwritingId underwritingId,
        String tenantId
) {
}