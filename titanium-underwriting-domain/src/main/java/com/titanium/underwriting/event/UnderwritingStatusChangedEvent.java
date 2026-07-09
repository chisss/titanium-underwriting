package com.titanium.underwriting.event;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * Underwriting Status Changed Event
 */
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
