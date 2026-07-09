package com.titanium.underwriting.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * Underwrite Command
 */
public record UnderwriteCommand(
        @TargetAggregateIdentifier UnderwritingId underwritingId,
        UnderwritingAmount amount,
        String reason,
        String processedBy,
        String tenantId
) {
}
