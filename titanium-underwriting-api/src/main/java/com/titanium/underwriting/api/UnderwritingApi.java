package com.titanium.underwriting.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.underwriting.UnderwriteRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;

/**
 * 核保服务Feign客户端
 */
@FeignClient(name = "titanium-underwriting-service", path = "/underwriting/api")
public interface UnderwritingApi {
    /**
     * 创建核保
     *
     * @param request 创建核保请求
     * @param tenantId 租户ID
     * @return 创建的核保DTO
     */
    @PostMapping("/create")
    ResponseEntity<UnderwritingResponse> createUnderwriting(@RequestBody CreateUnderwritingRequest request,
                                                       @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 根据ID查询核保
     *
     * @param underwritingId 核保ID
     * @param tenantId 租户ID
     * @return 核保DTO
     */
    @GetMapping("/{underwritingId}")
    ResponseEntity<UnderwritingResponse> getUnderwritingById(@PathVariable("underwritingId") String underwritingId,
                                                        @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 根据保单ID查询核保
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 核保DTO
     */
    @GetMapping("/policy/{policyId}")
    ResponseEntity<List<UnderwritingResponse>> getUnderwritingByPolicyId(@PathVariable("policyId") String policyId,
                                                              @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 执行核保
     *
     * @param underwritingId 核保ID
     * @param request 核保请求
     * @param tenantId 租户ID
     * @return 更新后的核保DTO
     */
    @PutMapping("/{underwritingId}/underwrite")
    ResponseEntity<UnderwritingResponse> underwrite(@PathVariable("underwritingId") String underwritingId,
                                               @RequestBody UnderwriteRequest request,
                                               @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 提交核保结构化输入（富核保路径步骤1）：提交被保人健康告知/体检/职业/财务信息。
     *
     * @param underwritingId 核保ID
     * @param request 结构化输入请求
     * @param tenantId 租户ID
     * @return 更新后的核保DTO
     */
    @PutMapping("/{underwritingId}/inputs")
    ResponseEntity<UnderwritingResponse> submitInput(@PathVariable("underwritingId") String underwritingId,
                                                @RequestBody SubmitUnderwritingInputApiRequest request,
                                                @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 触发核保决策（富核保路径步骤2）：基于已提交输入产出结论/风险等级/加费。
     *
     * @param underwritingId 核保ID
     * @param request 决策请求
     * @param tenantId 租户ID
     * @return 决策后的核保DTO
     */
    @PutMapping("/{underwritingId}/decide")
    ResponseEntity<UnderwritingResponse> decide(@PathVariable("underwritingId") String underwritingId,
                                           @RequestBody DecideUnderwritingApiRequest request,
                                           @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 根据状态分页查询核保列表
     * <p>
     * 返回当前页内容；分页元数据（总数/总页数）不在本契约内，需要时走 web 侧 {@code /search} 端点。
     * </p>
     *
     * @param status 核保状态
     * @param page 页码，从 0 开始
     * @param size 每页条数，服务端上限 200
     * @param tenantId 租户ID
     * @return 当前页核保DTO列表
     */
    @GetMapping("/status/{status}")
    ResponseEntity<List<UnderwritingResponse>> getUnderwritingsByStatus(@PathVariable("status") String status,
                                                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                                                   @RequestParam(value = "size", defaultValue = "20") int size,
                                                                   @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 分页查询全部核保
     * <p>
     * 返回当前页内容；分页元数据（总数/总页数）不在本契约内，需要时走 web 侧 {@code /search} 端点。
     * </p>
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数，服务端上限 200
     * @param tenantId 租户ID
     * @return 当前页核保DTO列表
     */
    @GetMapping("/all")
    ResponseEntity<List<UnderwritingResponse>> getAllUnderwritings(@RequestParam(value = "page", defaultValue = "0") int page,
                                                             @RequestParam(value = "size", defaultValue = "20") int size,
                                                             @RequestHeader("X-Tenant-ID") String tenantId);
}
