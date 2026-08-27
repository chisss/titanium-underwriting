package com.titanium.underwriting.web.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.underwriting.api.MaintenanceUnderwritingApi;
import com.titanium.underwriting.api.request.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.response.MaintenanceUnderwritingResponse;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.valueobject.MaintenanceRiskFieldChange;
import com.titanium.underwriting.valueobject.UnderwritingId;

import lombok.RequiredArgsConstructor;

/** 保全核保正式契约实现，只负责协议转换和命令派发。 */
@RestController
@RequestMapping("/underwriting/api/maintenance-assessments")
@RequiredArgsConstructor
public class MaintenanceUnderwritingApiProvider implements MaintenanceUnderwritingApi {

    private final UnderwritingCommandService underwritingCommandService;

    @Override
    public ResponseEntity<MaintenanceUnderwritingResponse> assess(
            AssessMaintenanceUnderwritingRequest request,
            String tenantId) {
        AssessMaintenanceUnderwritingCommand command = new AssessMaintenanceUnderwritingCommand(
                UnderwritingId.forMaintenance(tenantId, request.idempotencyKey()),
                tenantId,
                request.maintenanceId(),
                request.policyId(),
                request.policyBaselineVersion(),
                request.productId(),
                request.productVersion(),
                request.planVersion(),
                request.itemCode(),
                request.configurationVersion(),
                request.configurationContentHash(),
                request.configurationRequiresUnderwriting(),
                request.riskFieldChanges().stream()
                        .map(change -> new MaintenanceRiskFieldChange(
                                change.objectId(), change.fieldCode(), change.dataType(),
                                change.beforeValue(), change.proposedValue(), change.changeTypeCode()))
                        .toList(),
                request.idempotencyKey(),
                request.payloadHash(),
                request.requestedBy());
        return ResponseEntity.ok(toResponse(underwritingCommandService.assessMaintenance(command)));
    }

    private MaintenanceUnderwritingResponse toResponse(MaintenanceUnderwritingAssessedEvent event) {
        return new MaintenanceUnderwritingResponse(
                event.tenantId(), event.maintenanceId(), event.policyId(), event.policyBaselineVersion(),
                event.itemCode(), event.underwritingId().value(), event.idempotencyKey(), event.payloadHash(),
                event.ruleVersion(), event.modelVersion(), event.conclusion().name(),
                event.additionalConditions(), event.summary(), event.completedAt());
    }
}
