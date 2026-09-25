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
 * <p>
 * 🔴 m24-06（API-06）路径规范化：基路径由 {@code /underwriting/api/maintenance-assessments} 改为
 * {@code /api/v1/maintenance-assessments}；服务端（{@code MaintenanceUnderwritingApiProvider}）在
 * **一个版本周期内同时挂新旧两条路径**，待调用方迁移完毕再删旧路径。
 * </p>
 */
@FeignClient(
        name = "titanium-underwriting-service",
        contextId = "maintenanceUnderwritingApi",
        path = "/api/v1/maintenance-assessments")
public interface MaintenanceUnderwritingApi {

    /** 创建或重试一次幂等的保全风险评估。 */
    @PostMapping
    ApiResponse<MaintenanceUnderwritingResponse> assess(
            @RequestBody AssessMaintenanceUnderwritingRequest request);
}
