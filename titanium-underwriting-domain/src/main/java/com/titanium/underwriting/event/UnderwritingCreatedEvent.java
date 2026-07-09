package com.titanium.underwriting.event;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * Underwriting Created Event
 */
public record UnderwritingCreatedEvent(UnderwritingId underwritingId, PolicyId policyId, CustomerId customerId,
                                       UnderwritingAmount amount, UnderwritingEnum.UnderwritingType underwritingType,
                                       LocalDateTime createdAt, String createdBy, String tenantId) {
}
