package com.titanium.underwriting.domain.query;

import com.titanium.underwriting.domain.valueobject.PolicyId;

/**
 * 根据保单ID查询核保
 * <p>
 * 用于查询指定保单的核保信息
 * </p>
 */
public record FindUnderwritingByPolicyIdQuery(
        PolicyId policyId,
        String tenantId
) {
}
