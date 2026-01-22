package com.titanium.underwriting.query.query;

import com.titanium.underwriting.domain.valueobject.PolicyId;

/**
 * 根据保单ID查询核保信息
 */
public record FindUnderwritingByPolicyIdQuery(PolicyId policyId, String tenantId) {
}