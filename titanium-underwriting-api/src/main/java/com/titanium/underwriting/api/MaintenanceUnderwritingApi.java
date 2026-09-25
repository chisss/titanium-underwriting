package com.titanium.underwriting.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.underwriting.api.request.maintenance.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.response.maintenance.MaintenanceUnderwritingResponse;

/**
 * 保全场景专用核保契约，避免复用新单固定健康输入模型。
 * <p>
 * 🔴 m24-01a 起统一返回 {@link ApiResponse} 信封：调用方先判 {@code isSuccess()} 再取 {@code getData()}；
 * 失败仍由全局异常处理器以非 2xx 表达（Feign 抛 {@code FeignException}），信封化只改成功体的成形。
 * </p>
 */
@FeignClient(
        name = "titanium-underwriting-service",
        contextId = "maintenanceUnderwritingApi",
        path = "/underwriting/api/maintenance-assessments")
public interface MaintenanceUnderwritingApi {

    /** 创建或重试一次幂等的保全风险评估。 */
    @PostMapping
    ApiResponse<MaintenanceUnderwritingResponse> assess(
            @RequestBody AssessMaintenanceUnderwritingRequest request);
}
