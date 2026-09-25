package com.titanium.underwriting.web.provider;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.underwriting.api.MaintenanceUnderwritingApi;
import com.titanium.underwriting.api.request.maintenance.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.response.maintenance.MaintenanceUnderwritingResponse;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.web.mapper.MaintenanceUnderwritingWebMapper;

import lombok.RequiredArgsConstructor;

/**
 * 保全核保正式契约实现（Provider），只负责协议转换和命令派发。
 * <p>
 * 🔴 {@code @FeignClient(path)} 是 Feign 专有注记，<b>不被 Spring MVC 继承</b>，故本类必须以类级
 * {@code @RequestMapping} 重新声明同一路径；m24-06（API-06）起契约路径带版本段 {@code /api/v1}，
 * 本类在**一个版本周期内同时挂新旧两条路径**，待调用方迁移完毕再删旧路径。
 * </p>
 */
@RestController
@RequestMapping({"/api/v1/maintenance-assessments", "/underwriting/api/maintenance-assessments"})
@RequiredArgsConstructor
public class MaintenanceUnderwritingApiProvider implements MaintenanceUnderwritingApi {

    private final UnderwritingCommandService underwritingCommandService;
    private final MaintenanceUnderwritingWebMapper maintenanceUnderwritingWebMapper;

    @Override
    public ApiResponse<MaintenanceUnderwritingResponse> assess(
            AssessMaintenanceUnderwritingRequest request) {
        AssessMaintenanceUnderwritingCommand command = maintenanceUnderwritingWebMapper.toCommand(request, RequestContextHolder.requireTenantId());
        return ApiResponse.success(
                maintenanceUnderwritingWebMapper.toResponse(underwritingCommandService.assessMaintenance(command)));
    }
}
