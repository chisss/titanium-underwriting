package com.titanium.underwriting.web.provider;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.underwriting.UnderwriteRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.assembler.UnderwritingWebAssembler;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;

import lombok.RequiredArgsConstructor;

/**
 * 核保契约实现（Provider）
 * <p>
 * 承接 {@link UnderwritingApi} Feign 契约，面向其它微服务的远程调用。路径由 {@link UnderwritingApi} 的
 * {@code @RequestMapping("/underwriting/api")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（DTO/Request → 领域命令、读模型结果 → DTO）+ 调用应用层门面，
 * 零业务逻辑。与面向后台/端上的 {@code UnderwritingController} 平行收敛到同一应用层门面。
 * </p>
 */
@RestController
@RequestMapping("/underwriting/api")
@RequiredArgsConstructor
public class UnderwritingApiProvider implements UnderwritingApi {

    /** 单页最大条数：契约侧可传任意 size，读库须自我保护（超大 size 会退化为全表扫描） */
    private static final int MAX_PAGE_SIZE     = 200;

    /** 默认单页条数：与 {@link UnderwritingApi} 契约的 defaultValue 保持一致 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final UnderwritingCommandService  underwritingCommandService;
    private final UnderwritingQueryAppService underwritingQueryAppService;
    private final UnderwritingWebAssembler    underwritingWebAssembler;
    private final UnderwritingWebMapper       underwritingWebMapper;

    @Override
    public ResponseEntity<UnderwritingResponse> createUnderwriting(CreateUnderwritingRequest request, String tenantId) {
        // 协议转换：远程 Request → 领域命令，发命令后回查读模型组装对外 Response
        CreateUnderwritingCommand command = underwritingWebAssembler.toCommand(request, tenantId);
        underwritingCommandService.createUnderwriting(command);
        return new ResponseEntity<>(underwritingWebMapper.toResponse(command), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<UnderwritingResponse> getUnderwritingById(String underwritingId, String tenantId) {
        return ResponseEntity.ok(queryResponse(underwritingId, tenantId));
    }

    @Override
    public ResponseEntity<List<UnderwritingResponse>> getUnderwritingByPolicyId(String policyId, String tenantId) {
        UnderwritingQueryResult result = underwritingQueryAppService
                .findUnderwritingByPolicyId(PolicyId.of(policyId), tenantId);
        List<UnderwritingResponse> body = result != null ? List.of(underwritingWebMapper.toResponse(result)) : List.of();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<UnderwritingResponse> underwrite(String underwritingId, UnderwriteRequest request,
                                                      String tenantId) {
        UnderwriteCommand command = underwritingWebAssembler.toCommand(underwritingId, request, tenantId);
        UnderwritingStatusChangedEvent event = underwritingCommandService.underwrite(command);
        return ResponseEntity.ok(underwritingWebMapper.toResponse(event));
    }

    @Override
    public ResponseEntity<UnderwritingResponse> submitInput(String underwritingId,
            SubmitUnderwritingInputApiRequest request, String tenantId) {
        SubmitUnderwritingInputCommand command = underwritingWebAssembler.toCommand(underwritingId, request, tenantId);
        UnderwritingInputSubmittedEvent event = underwritingCommandService.submitInput(command);
        return ResponseEntity.ok(underwritingWebMapper.toResponse(event));
    }

    @Override
    public ResponseEntity<UnderwritingResponse> decide(String underwritingId, DecideUnderwritingApiRequest request,
            String tenantId) {
        DecideUnderwritingCommand command = underwritingWebAssembler.toCommand(underwritingId, request, tenantId);
        // UW-4：透传险种编码，application 层据此读取产品核保配置充实决策命令（加费许可等）
        UnderwritingDecidedEvent event = underwritingCommandService.decide(command, request.getProductCode());
        return ResponseEntity.ok(underwritingWebMapper.toResponse(event));
    }

    @Override
    public ResponseEntity<List<UnderwritingResponse>> getUnderwritingsByStatus(String status, int page, int size,
            String tenantId) {
        Page<UnderwritingQueryResult> results = underwritingQueryAppService.findUnderwritingsByStatus(
                UnderwritingEnum.UnderwritingStatus.fromCode(status), pageRequest(page, size), tenantId);
        return ResponseEntity.ok(toResponses(results));
    }

    @Override
    public ResponseEntity<List<UnderwritingResponse>> getAllUnderwritings(int page, int size, String tenantId) {
        // 全量即「多条件全空」的动态查询：读侧 Specification 逐条跳过 null 条件，无需为契约新增 findAll 方法
        Page<UnderwritingQueryResult> results = underwritingQueryAppService.findUnderwritingsByMultipleConditions(null,
                null, null, null, null, null, null, pageRequest(page, size), tenantId);
        return ResponseEntity.ok(toResponses(results));
    }

    /**
     * 按核保ID查读模型并转对外 Response
     */
    private UnderwritingResponse queryResponse(String underwritingId, String tenantId) {
        UnderwritingQueryResult result = underwritingQueryAppService
                .findUnderwritingById(new UnderwritingId(underwritingId), tenantId);
        return underwritingWebMapper.toResponse(result);
    }

    /**
     * 归一化契约侧分页参数：页码下限 0，单页条数上限 {@link #MAX_PAGE_SIZE}，非法 size 回落默认值
     * <p>
     * 远程契约可传任意 size，读库须自我保护——超大 size 会退化为全表扫描。
     * </p>
     */
    private Pageable pageRequest(int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize);
    }

    /**
     * 读模型结果页 → 对外 Response 列表（分页元数据不进契约，见 {@link UnderwritingApi} 方法说明）
     */
    private List<UnderwritingResponse> toResponses(Page<UnderwritingQueryResult> results) {
        return results.getContent().stream().map(underwritingWebMapper::toResponse).toList();
    }
}
