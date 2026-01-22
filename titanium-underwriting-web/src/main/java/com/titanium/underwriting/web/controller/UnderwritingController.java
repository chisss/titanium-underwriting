package com.titanium.underwriting.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;
import com.titanium.underwriting.web.vo.UnderwritingVO;

import lombok.RequiredArgsConstructor;

/**
 * 核保控制器
 */
@RestController
@RequestMapping("/underwritings/web")
@RequiredArgsConstructor
public class UnderwritingController {

    private final UnderwritingApi       underwritingApi;
    private final UnderwritingWebMapper underwritingWebMapper;

    /**
     * 创建核保
     *
     * @param request 创建核保请求
     * @return 创建的核保VO
     */
    @PostMapping
    public ResponseEntity<UnderwritingVO> createUnderwriting(@RequestBody CreateUnderwritingRequest request,
                                                             @RequestHeader("X-Tenant-ID") String tenantId) {
        ResponseEntity<UnderwritingDTO> underwritingResponse = underwritingApi.createUnderwriting(request, tenantId);
        return new ResponseEntity<>(underwritingWebMapper.toVO(underwritingResponse.getBody()), HttpStatus.CREATED);
    }

    /**
     * 根据ID查询核保
     *
     * @param underwritingId 核保ID
     * @return 核保VO
     */
    @GetMapping("/{underwritingId}")
    public ResponseEntity<UnderwritingVO> getUnderwritingById(@PathVariable String underwritingId,
                                                              @RequestHeader("X-Tenant-ID") String tenantId) {

        ResponseEntity<UnderwritingDTO> underwritingResponse = underwritingApi.getUnderwritingById(underwritingId,
                tenantId);
        return ResponseEntity.ok(underwritingWebMapper.toVO(underwritingResponse.getBody()));
    }

    /**
     * 执行核保
     *
     * @param underwritingId 核保ID
     * @param request 核保请求
     * @return 更新后的核保VO
     */
    @PutMapping("/{underwritingId}/underwrite")
    public ResponseEntity<UnderwritingVO> underwrite(@PathVariable String underwritingId,
                                                     @RequestBody UnderwriteRequest request,
                                                     @RequestHeader("X-Tenant-ID") String tenantId) {

        ResponseEntity<UnderwritingDTO> underwriteResponse = underwritingApi.underwrite(underwritingId, request,
                tenantId);
        return ResponseEntity.ok(underwritingWebMapper.toVO(underwriteResponse.getBody()));
    }

    /**
     * 根据保单ID查询核保
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 核保VO
     */
    public ResponseEntity<UnderwritingVO> getUnderwritingByPolicyId(String policyId, String tenantId) {
        // TODO: 需要在UnderwritingApi中添加根据保单ID查询的方法
        return ResponseEntity.notFound().build();
    }

    /**
     * 根据状态查询核保列表
     *
     * @param status 核保状态
     * @param tenantId 租户ID
     * @return 核保VO列表
     */
    public ResponseEntity<List<UnderwritingVO>> getUnderwritingsByStatus(String status, String tenantId) {
        // TODO: 需要在UnderwritingApi中添加根据状态查询的方法
        return ResponseEntity.ok(List.of());
    }

    /**
     * 查询所有核保列表
     *
     * @param tenantId 租户ID
     * @return 核保VO列表
     */
    public ResponseEntity<List<UnderwritingVO>> getAllUnderwritings(String tenantId) {
        // TODO: 需要在UnderwritingApi中添加查询所有核保的方法
        return ResponseEntity.ok(List.of());
    }
}
