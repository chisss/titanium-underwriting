package com.titanium.underwriting.query.query;

import com.titanium.underwriting.domain.valueobject.UnderwritingId;

/**
 * 查询特定ID的核保信息
 */
public record FindUnderwritingByIdQuery(UnderwritingId underwritingId, String tenantId) {
}
