package com.titanium.underwriting.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.underwriting.api.request.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.response.MaintenanceUnderwritingResponse;

/** 保全场景专用核保契约，避免复用新单固定健康输入模型。 */
@FeignClient(
        name = "titanium-underwriting-service",
        contextId = "maintenanceUnderwritingApi",
        path = "/underwriting/api/maintenance-assessments")
public interface MaintenanceUnderwritingApi {

    /** 创建或重试一次幂等的保全风险评估。 */
    @PostMapping
    ResponseEntity<MaintenanceUnderwritingResponse> assess(
            @RequestBody AssessMaintenanceUnderwritingRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId);
}
