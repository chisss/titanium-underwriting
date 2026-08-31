package com.titanium.underwriting.web.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.underwriting.api.MaintenanceUnderwritingApi;
import com.titanium.underwriting.api.request.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.response.MaintenanceUnderwritingResponse;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.web.mapper.MaintenanceUnderwritingWebMapper;

import lombok.RequiredArgsConstructor;

/** 保全核保正式契约实现，只负责协议转换和命令派发。 */
@RestController
@RequestMapping("/underwriting/api/maintenance-assessments")
@RequiredArgsConstructor
public class MaintenanceUnderwritingApiProvider implements MaintenanceUnderwritingApi {

    private final UnderwritingCommandService underwritingCommandService;
    private final MaintenanceUnderwritingWebMapper maintenanceUnderwritingWebMapper;

    @Override
    public ResponseEntity<MaintenanceUnderwritingResponse> assess(
            AssessMaintenanceUnderwritingRequest request,
            String tenantId) {
        AssessMaintenanceUnderwritingCommand command = maintenanceUnderwritingWebMapper.toCommand(request, tenantId);
        return ResponseEntity.ok(
                maintenanceUnderwritingWebMapper.toResponse(underwritingCommandService.assessMaintenance(command)));
    }
}
