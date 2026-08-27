package com.titanium.underwriting.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.underwriting.valueobject.MaintenanceUnderwritingConclusion;
import com.titanium.underwriting.valueobject.UnderwritingId;

/** 保全核保评估已形成版本化权威结论。 */
public record MaintenanceUnderwritingAssessedEvent(
        UnderwritingId underwritingId,
        String tenantId,
        String maintenanceId,
        String policyId,
        Long policyBaselineVersion,
        String itemCode,
        String idempotencyKey,
        String payloadHash,
        String ruleVersion,
        String modelVersion,
        MaintenanceUnderwritingConclusion conclusion,
        List<String> additionalConditions,
        String summary,
        LocalDateTime completedAt,
        LocalDateTime assessedAt,
        String assessedBy) {

    public MaintenanceUnderwritingAssessedEvent {
        additionalConditions = additionalConditions == null ? List.of() : List.copyOf(additionalConditions);
    }
}
