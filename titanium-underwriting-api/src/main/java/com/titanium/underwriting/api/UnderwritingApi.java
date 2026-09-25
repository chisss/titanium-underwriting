package com.titanium.underwriting.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.underwriting.UnderwriteRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;

/**
 * 核保服务Feign客户端
 * <p>
 * 🔴 <b>统一响应信封</b>：各方法均返回 {@link ApiResponse} 信封（全域唯一实现，位于 titanium-metadata），
 * 业务载荷置于 {@code data}。调用方须先判 {@code isSuccess()} 再取 {@code getData()}；
 * 失败仍由全局异常处理器以非 2xx 表达（Feign 照旧抛 {@code FeignException}），信封化只改成功体的成形。
 * </p>
 * <p>
 * {@link #createUnderwriting} 保留 {@code ResponseEntity} 外层，因其需表达 201 Created 状态语义
 * （状态码控制）；其余方法无状态控制需求，一律裸 {@code ApiResponse<T>}。
 * </p>
 */
@FeignClient(name = "titanium-underwriting-service", path = "/underwriting/api")
public interface UnderwritingApi {
    /**
     * 创建核保
     *
     * @param request 创建核保请求
     * @return 统一信封（HTTP 201），{@code data} 为创建的核保DTO
     */
    @PostMapping("/create")
    ResponseEntity<ApiResponse<UnderwritingResponse>> createUnderwriting(@RequestBody CreateUnderwritingRequest request);

    /**
     * 根据ID查询核保
     *
     * @param underwritingId 核保ID
     * @return 统一信封，{@code data} 为核保DTO（未命中时为 {@code null}）
     */
    @GetMapping("/{underwritingId}")
    ApiResponse<UnderwritingResponse> getUnderwritingById(@PathVariable String underwritingId);

    /**
     * 根据保单ID查询核保
     *
     * @param policyId 保单ID
     * @return 统一信封，{@code data} 为核保DTO列表（无命中为空列表）
     */
    @GetMapping("/policy/{policyId}")
    ApiResponse<List<UnderwritingResponse>> getUnderwritingByPolicyId(@PathVariable String policyId);

    /**
     * 执行核保
     *
     * @param underwritingId 核保ID
     * @param request 核保请求
     * @return 统一信封，{@code data} 为更新后的核保DTO
     */
    @PutMapping("/{underwritingId}/underwrite")
    ApiResponse<UnderwritingResponse> underwrite(@PathVariable String underwritingId,
                                               @RequestBody UnderwriteRequest request);

    /**
     * 提交核保结构化输入（富核保路径步骤1）：提交被保人健康告知/体检/职业/财务信息。
     *
     * @param underwritingId 核保ID
     * @param request 结构化输入请求
     * @return 统一信封，{@code data} 为更新后的核保DTO
     */
    @PutMapping("/{underwritingId}/inputs")
    ApiResponse<UnderwritingResponse> submitInput(@PathVariable String underwritingId,
                                                @RequestBody SubmitUnderwritingInputApiRequest request);

    /**
     * 触发核保决策（富核保路径步骤2）：基于已提交输入产出结论/风险等级/加费。
     *
     * @param underwritingId 核保ID
     * @param request 决策请求
     * @return 统一信封，{@code data} 为决策后的核保DTO
     */
    @PutMapping("/{underwritingId}/decide")
    ApiResponse<UnderwritingResponse> decide(@PathVariable String underwritingId,
                                           @RequestBody DecideUnderwritingApiRequest request);

    /**
     * 根据状态分页查询核保列表
     * <p>
     * 返回当前页内容；分页元数据（总数/总页数）不在本契约内，需要时走 web 侧 {@code /search} 端点。
     * </p>
     *
     * @param status 核保状态
     * @param page 页码，从 0 开始
     * @param size 每页条数，服务端上限 200
     * @return 统一信封，{@code data} 为当前页核保DTO列表
     */
    @GetMapping("/status/{status}")
    ApiResponse<List<UnderwritingResponse>> getUnderwritingsByStatus(@PathVariable String status,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size);

    /**
     * 分页查询全部核保
     * <p>
     * 返回当前页内容；分页元数据（总数/总页数）不在本契约内，需要时走 web 侧 {@code /search} 端点。
     * </p>
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数，服务端上限 200
     * @return 统一信封，{@code data} 为当前页核保DTO列表
     */
    @GetMapping("/all")
    ApiResponse<List<UnderwritingResponse>> getAllUnderwritings(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size);
}
