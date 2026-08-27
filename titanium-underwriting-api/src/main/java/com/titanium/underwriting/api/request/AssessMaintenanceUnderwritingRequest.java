package com.titanium.underwriting.api.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 保全核保评估请求；字段值是规范化原值，仅允许在受信服务链路传输。 */
@Schema(description = "保全核保评估请求")
public record AssessMaintenanceUnderwritingRequest(
        String maintenanceId,
        String policyId,
        Long policyBaselineVersion,
        String productId,
        String productVersion,
        String planVersion,
        String itemCode,
        String configurationVersion,
        String configurationContentHash,
        boolean configurationRequiresUnderwriting,
        List<RiskFieldChangeRequest> riskFieldChanges,
        String idempotencyKey,
        String payloadHash,
        String requestedBy) {

    public AssessMaintenanceUnderwritingRequest {
        riskFieldChanges = riskFieldChanges == null ? List.of() : List.copyOf(riskFieldChanges);
    }

    /** 单个风险相关字段的基准值与拟变更值。 */
    public record RiskFieldChangeRequest(
            String objectId,
            String fieldCode,
            String dataType,
            String beforeValue,
            String proposedValue,
            String changeTypeCode) {
    }
}
