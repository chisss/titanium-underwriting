package com.titanium.underwriting.domain.command;

import com.titanium.underwriting.domain.valueobject.UnderwritingAmount;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

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