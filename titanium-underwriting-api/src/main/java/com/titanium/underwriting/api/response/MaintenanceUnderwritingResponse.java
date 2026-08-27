package com.titanium.underwriting.api.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Underwriting 返回给 Maintenance 的冻结风险结论。 */
@Schema(description = "保全核保风险结论")
public record MaintenanceUnderwritingResponse(
        String tenantId,
        String maintenanceId,
        String policyId,
        Long policyBaselineVersion,
        String itemCode,
        String underwritingCaseId,
        String idempotencyKey,
        String payloadHash,
        String ruleVersion,
        String modelVersion,
        String conclusion,
        List<String> additionalConditions,
        String summary,
        LocalDateTime completedAt) {

    public MaintenanceUnderwritingResponse {
        additionalConditions = additionalConditions == null ? List.of() : List.copyOf(additionalConditions);
    }
}
