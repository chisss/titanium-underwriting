package com.titanium.underwriting.web.provider;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;
import com.titanium.underwriting.api.response.UnderwritingResponse;
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
    public ResponseEntity<List<UnderwritingResponse>> getUnderwritingsByStatus(String status, String tenantId) {
        // TODO: 待读侧补齐按状态列表查询后接通（读模型分页查询已在 QueryAppService，DTO 契约缺分页参数暂留空）
        return ResponseEntity.ok(List.of());
    }

    @Override
    public ResponseEntity<List<UnderwritingResponse>> getAllUnderwritings(String tenantId) {
        // TODO: 待读侧补齐全量查询后接通（DTO 契约缺分页/租户维度参数暂留空）
        return ResponseEntity.ok(List.of());
    }

    /**
     * 按核保ID查读模型并转对外 Response
     */
    private UnderwritingResponse queryResponse(String underwritingId, String tenantId) {
        UnderwritingQueryResult result = underwritingQueryAppService
                .findUnderwritingById(new UnderwritingId(underwritingId), tenantId);
        return underwritingWebMapper.toResponse(result);
    }
}
