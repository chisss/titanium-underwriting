package com.titanium.underwriting.domain.command;

import com.titanium.underwriting.domain.valueobject.UnderwritingId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Manual Review Command
 */
public record ManualReviewCommand(
        @TargetAggregateIdentifier UnderwritingId underwritingId,
        String reviewComments,
        String reviewedBy,
        String tenantId
) {
}