package com.titanium.underwriting.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * Underwriting Status Changed Event
 */
@Revision("1")
public record UnderwritingStatusChangedEvent(
        UnderwritingId underwritingId,
        UnderwritingEnum.UnderwritingStatus oldStatus,
        UnderwritingEnum.UnderwritingStatus newStatus,
        String reason,
        LocalDateTime changedAt,
        String changedBy,
        String tenantId
) {
}
