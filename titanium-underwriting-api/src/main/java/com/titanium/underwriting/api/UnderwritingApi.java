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
import com.titanium.underwriting.api.request.underwriting.AutoDecideUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.ManualReviewRequest;
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
 * <p>
 * 🔴 m24-06（API-06）路径规范化：基路径由 {@code /underwriting/api} 改为 {@code /api/v1/underwritings}，
 * 且原方法级 {@code @PostMapping("/create")} 的动词段 {@code /create} 被去掉，改为对**资源集合**直接发
 * {@code POST}（AIP 惯例）。服务端（{@code UnderwritingApiProvider}）在**一个版本周期内同时挂新旧两条路径**，
 * 待调用方迁移完毕再删旧路径。
 * </p>
 */
@FeignClient(name = "titanium-underwriting-service", path = "/api/v1/underwritings")
public interface UnderwritingApi {
    /**
     * 创建核保
     *
     * @param request 创建核保请求
     * @return 统一信封（HTTP 201），{@code data} 为创建的核保DTO
     */
    @PostMapping
    ResponseEntity<ApiResponse<UnderwritingResponse>> createUnderwriting(@RequestBody CreateUnderwritingRequest request);

    /**
     * 自动决策（粗粒度，g02-04 / AC-05）：一次调用完成「创建/幂等复用 + 提交输入 + 出具决策」并返回结论。
     * <p>
     * 🔴 <b>幂等键为投保单号</b>：同一 {@code insuranceId} 重复调用返回同一张核保单的结论，不重复建单；
     * 上游 Saga 重试/重入安全。既有四步接口（创建/提交输入/决策）保持不变，二者为粗/细两种粒度。
     * </p>
     * <p>
     * 🔴 <b>路径实测说明</b>：本方法挂在本接口既有的 {@code path = "/api/v1/underwritings"} 之下，
     * 服务端真实路径为 {@code POST /api/v1/underwritings/:auto-decide}。规划文档所写的
     * {@code /api/v1/underwritings:auto-decide}（冒号紧贴资源名）在 Spring 下<b>不可达</b>——
     * 实测 {@code PathPattern.combine} 与 Feign 的 {@code SpringMvcContract} <b>均强制在方法级相对路径前插入
     * {@code /}</b>（两侧行为一致，已逐形态验证）；要得到无斜杠形态必须让类级前缀降为 {@code /api/v1}
     * （即新建一套契约与 Provider），代价与收益不成比例（KISS），故取等价的斜杠形态并在此登记偏差。
     * </p>
     *
     * @param request 自动决策请求（投保单号 + 险种 + 保额 + 风险要素）
     * @return 统一信封，{@code data} 为核保结论（新建与复用同形）
     */
    @PostMapping(":auto-decide")
    ApiResponse<UnderwritingResponse> autoDecide(@RequestBody AutoDecideUnderwritingRequest request);

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
     * 转人工审核（g02-03）：把核保件置为人工复核状态（{@code MANUAL_REVIEW}）。
     * <p>
     * 服务端与 web 端点平行收敛到同一应用层门面 {@code UnderwritingCommandService#manualReview}；
     * 已出结论的终态核保件发起转人工会被聚合拒绝（不产生事件）。
     * </p>
     *
     * @param underwritingId 核保ID
     * @param request 转人工审核请求
     * @return 统一信封，{@code data} 为更新后的核保DTO
     */
    @PutMapping("/{underwritingId}/manual-review")
    ApiResponse<UnderwritingResponse> manualReview(@PathVariable String underwritingId,
                                                   @RequestBody ManualReviewRequest request);

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
