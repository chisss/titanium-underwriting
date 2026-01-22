package com.titanium.underwriting.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.underwriting.domain.valueobject.CustomerId;
import com.titanium.underwriting.domain.valueobject.PolicyId;
import com.titanium.underwriting.domain.valueobject.UnderwritingAmount;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;

/**
 * Create Underwriting Command
 */
public record CreateUnderwritingCommand(@TargetAggregateIdentifier UnderwritingId underwritingId, PolicyId policyId,
                                        CustomerId customerId, UnderwritingAmount amount, String underwritingType,
                                        String createdBy, String tenantId) {
}
