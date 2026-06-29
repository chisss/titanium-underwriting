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
import org.springframework.web.bind.annotation.RequestMapping;

import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;

/**
 * 核保服务Feign客户端
 */
@FeignClient(name = "titanium-underwriting-service")
@RequestMapping("/underwriting/api")
public interface UnderwritingApi {
    /**
     * 创建核保
     *
     * @param request 创建核保请求
     * @param tenantId 租户ID
     * @return 创建的核保DTO
     */
    @PostMapping("/create")
    ResponseEntity<UnderwritingDTO> createUnderwriting(@RequestBody CreateUnderwritingRequest request,
                                                       @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 根据ID查询核保
     *
     * @param underwritingId 核保ID
     * @param tenantId 租户ID
     * @return 核保DTO
     */
    @GetMapping("/{underwritingId}")
    ResponseEntity<UnderwritingDTO> getUnderwritingById(@PathVariable("underwritingId") String underwritingId,
                                                        @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 根据保单ID查询核保
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 核保DTO
     */
    @GetMapping("/policy/{policyId}")
    ResponseEntity<List<UnderwritingDTO>> getUnderwritingByPolicyId(@PathVariable("policyId") String policyId,
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
    ResponseEntity<UnderwritingDTO> underwrite(@PathVariable("underwritingId") String underwritingId,
                                               @RequestBody UnderwriteRequest request,
                                               @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 根据状态查询核保列表
     *
     * @param status 核保状态
     * @param tenantId 租户ID
     * @return 核保DTO列表
     */
    @GetMapping("/status/{status}")
    ResponseEntity<List<UnderwritingDTO>> getUnderwritingsByStatus(@PathVariable("status") String status,
                                                                   @RequestHeader("X-Tenant-ID") String tenantId);

    /**
     * 查询所有核保
     *
     * @param tenantId 租户ID
     * @return 核保DTO列表
     */
    @GetMapping("/all")
    ResponseEntity<List<UnderwritingDTO>> getAllUnderwritings(@RequestHeader("X-Tenant-ID") String tenantId);
}
