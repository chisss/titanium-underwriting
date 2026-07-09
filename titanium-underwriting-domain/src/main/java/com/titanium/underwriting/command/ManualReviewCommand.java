package com.titanium.underwriting.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.underwriting.valueobject.UnderwritingId;

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
