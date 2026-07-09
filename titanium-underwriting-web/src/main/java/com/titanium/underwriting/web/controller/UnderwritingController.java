package com.titanium.underwriting.web.controller;

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

import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;
import com.titanium.underwriting.web.request.CreateUnderwritingRequest;
import com.titanium.underwriting.web.request.DecideUnderwritingRequest;
import com.titanium.underwriting.web.request.SubmitUnderwritingInputRequest;
import com.titanium.underwriting.web.request.UnderwriteRequest;
import com.titanium.underwriting.web.vo.UnderwritingVO;

import lombok.RequiredArgsConstructor;

/**
 * 核保控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/underwritings}，入参 web 层 {@code XxxRequest}、出参
 * {@code UnderwritingVO}，<b>不 implements UnderwritingApi</b>（远程契约由
 * {@code UnderwritingApiProvider} 承接）。表现层经 {@link UnderwritingWebMapper} 把 Request 转为
 * CQRS 命令交 {@link UnderwritingCommandService}，读入口查读模型经 {@link UnderwritingQueryAppService}
 * 后转 VO。web 可依赖 command/query，但不碰聚合根。与 {@code UnderwritingApiProvider} 平行收敛到同一门面。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/underwritings")
@RequiredArgsConstructor
public class UnderwritingController {

    private final UnderwritingCommandService  underwritingCommandService;
    private final UnderwritingQueryAppService underwritingQueryAppService;
    private final UnderwritingWebMapper       underwritingWebMapper;

    /**
     * 创建核保
     *
     * @param request  创建核保请求
     * @param tenantId 租户ID
     * @return 创建的核保VO
     */
    @PostMapping
    public ResponseEntity<UnderwritingVO> createUnderwriting(@RequestBody CreateUnderwritingRequest request,
                                                             @RequestHeader("X-Tenant-ID") String tenantId) {
        CreateUnderwritingCommand command = underwritingWebMapper.toCommand(request, tenantId);
        String createdId = underwritingCommandService.createUnderwriting(command);
        return new ResponseEntity<>(queryVO(createdId, tenantId), HttpStatus.CREATED);
    }

    /**
     * 根据ID查询核保
     *
     * @param underwritingId 核保ID
     * @param tenantId       租户ID
     * @return 核保VO
     */
    @GetMapping("/{underwritingId}")
    public ResponseEntity<UnderwritingVO> getUnderwritingById(@PathVariable String underwritingId,
                                                              @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(queryVO(underwritingId, tenantId));
    }

    /**
     * 执行核保
     *
     * @param underwritingId 核保ID
     * @param request        核保请求
     * @param tenantId       租户ID
     * @return 更新后的核保VO
     */
    @PutMapping("/{underwritingId}/underwrite")
    public ResponseEntity<UnderwritingVO> underwrite(@PathVariable String underwritingId,
                                                     @RequestBody UnderwriteRequest request,
                                                     @RequestHeader("X-Tenant-ID") String tenantId) {
        UnderwriteCommand command = underwritingWebMapper.toCommand(underwritingId, request, tenantId);
        underwritingCommandService.underwrite(command);
        return ResponseEntity.ok(queryVO(underwritingId, tenantId));
    }

    /**
     * 提交险种专属核保输入（健康告知/体检/职业/车辆风险）
     *
     * @param underwritingId 核保ID
     * @param request        核保输入请求
     * @param tenantId       租户ID
     * @return 更新后的核保VO
     */
    @PutMapping("/{underwritingId}/inputs")
    public ResponseEntity<UnderwritingVO> submitInput(@PathVariable String underwritingId,
                                                      @RequestBody SubmitUnderwritingInputRequest request,
                                                      @RequestHeader("X-Tenant-ID") String tenantId) {
        SubmitUnderwritingInputCommand command = underwritingWebMapper.toCommand(underwritingId, request, tenantId);
        underwritingCommandService.submitInput(command);
        return ResponseEntity.ok(queryVO(underwritingId, tenantId));
    }

    /**
     * 触发核保决策（基于已提交的结构化输入产出结论与风险等级）
     *
     * @param underwritingId 核保ID
     * @param request        核保决策请求
     * @param tenantId       租户ID
     * @return 决策后的核保VO
     */
    @PutMapping("/{underwritingId}/decide")
    public ResponseEntity<UnderwritingVO> decide(@PathVariable String underwritingId,
                                                 @RequestBody DecideUnderwritingRequest request,
                                                 @RequestHeader("X-Tenant-ID") String tenantId) {
        DecideUnderwritingCommand command = underwritingWebMapper.toCommand(underwritingId, request, tenantId);
        underwritingCommandService.decide(command);
        return ResponseEntity.ok(queryVO(underwritingId, tenantId));
    }

    /**
     * 按核保ID查读模型并转展示 VO
     */
    private UnderwritingVO queryVO(String underwritingId, String tenantId) {
        UnderwritingQueryResult result = underwritingQueryAppService
                .findUnderwritingById(new UnderwritingId(underwritingId), tenantId);
        return underwritingWebMapper.toVO(result);
    }
}
