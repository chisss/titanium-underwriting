package com.titanium.underwriting.domain.event;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.valueobject.UnderwritingId;

import java.time.LocalDateTime;

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