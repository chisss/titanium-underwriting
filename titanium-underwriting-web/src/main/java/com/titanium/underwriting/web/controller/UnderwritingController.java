package com.titanium.underwriting.web.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.response.underwriting.UnderwritingStatisticsResponse;
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
import com.titanium.underwriting.query.result.UnderwritingStatisticsResult;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.assembler.UnderwritingWebAssembler;
import com.titanium.underwriting.web.dto.CreateUnderwritingDTO;
import com.titanium.underwriting.web.dto.DecideUnderwritingDTO;
import com.titanium.underwriting.web.dto.SubmitUnderwritingInputDTO;
import com.titanium.underwriting.web.dto.UnderwriteDTO;
import com.titanium.underwriting.web.mapper.UnderwritingStatisticsWebMapper;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;
import com.titanium.underwriting.web.vo.UnderwritingVO;

import lombok.RequiredArgsConstructor;

/**
 * 核保控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/underwritings}，入参 web 层 {@code XxxRequest}、出参
 * {@code UnderwritingVO}，<b>不 implements UnderwritingApi</b>（远程契约由
 * {@code UnderwritingApiProvider} 承接）。表现层经 {@link UnderwritingWebAssembler} 把 Request 装配为
 * CQRS 命令交 {@link UnderwritingCommandService}，读入口查读模型经 {@link UnderwritingWebMapper}
 * 转 VO。web 可依赖 command/query，但不碰聚合根。与 {@code UnderwritingApiProvider} 平行收敛到同一门面。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/underwritings")
@RequiredArgsConstructor
public class UnderwritingController {

    private final UnderwritingCommandService  underwritingCommandService;
    private final UnderwritingQueryAppService underwritingQueryAppService;
    private final UnderwritingWebAssembler    underwritingWebAssembler;
    private final UnderwritingWebMapper       underwritingWebMapper;
    private final UnderwritingStatisticsWebMapper underwritingStatisticsWebMapper;

    /**
     * 创建核保
     *
     * @param request  创建核保请求
     * @param tenantId 租户ID
     * @return 创建的核保VO
     */
    @PostMapping
    public ResponseEntity<UnderwritingVO> createUnderwriting(@RequestBody CreateUnderwritingDTO request,
                                                             @RequestHeader("X-Tenant-ID") String tenantId) {
        CreateUnderwritingCommand command = underwritingWebAssembler.toCommand(request, tenantId);
        // 返回编号后的命令（caseNo 已由 application 层发号填充），保证创建响应回显核保案号
        CreateUnderwritingCommand numbered = underwritingCommandService.createUnderwriting(command);
        return new ResponseEntity<>(underwritingWebMapper.toVO(underwritingWebMapper.toResponse(numbered)),
                HttpStatus.CREATED);
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
                                                     @RequestBody UnderwriteDTO request,
                                                     @RequestHeader("X-Tenant-ID") String tenantId) {
        UnderwriteCommand command = underwritingWebAssembler.toCommand(underwritingId, request, tenantId);
        UnderwritingStatusChangedEvent event = underwritingCommandService.underwrite(command);
        return ResponseEntity.ok(underwritingWebMapper.toVO(underwritingWebMapper.toResponse(event)));
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
                                                      @RequestBody SubmitUnderwritingInputDTO request,
                                                      @RequestHeader("X-Tenant-ID") String tenantId) {
        SubmitUnderwritingInputCommand command = underwritingWebAssembler.toCommand(underwritingId, request, tenantId);
        UnderwritingInputSubmittedEvent event = underwritingCommandService.submitInput(command);
        return ResponseEntity.ok(underwritingWebMapper.toVO(underwritingWebMapper.toResponse(event)));
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
                                                 @RequestBody DecideUnderwritingDTO request,
                                                 @RequestHeader("X-Tenant-ID") String tenantId) {
        DecideUnderwritingCommand command = underwritingWebAssembler.toCommand(underwritingId, request, tenantId);
        UnderwritingDecidedEvent event = underwritingCommandService.decide(command);
        return ResponseEntity.ok(underwritingWebMapper.toVO(underwritingWebMapper.toResponse(event)));
    }

    /**
     * 多条件组合搜索核保（状态、类型、风险等级与申请时间范围过滤，分页返回）
     * <p>
     * 通过 {@link UnderwritingQueryAppService#findUnderwritingsByMultipleConditions} 派发到读侧；
     * 枚举入参经 {@code fromCode} 空安全解析，非法 code 按不传处理而非抛异常。
     * </p>
     *
     * @param status          核保状态（可选，如 PENDING/STANDARD/RATED）
     * @param underwritingType 核保类型（可选，如 NEW_BUSINESS/RENEWAL/ENDORSEMENT/REINSTATEMENT）
     * @param riskLevel       风险等级（可选，如 LOW/MEDIUM/HIGH）
     * @param startTime       申请时间下限（可选，ISO-8601，如 2026-09-01T00:00:00）
     * @param endTime         申请时间上限（可选，ISO-8601）
     * @param page            页码（默认0）
     * @param size            每页大小（默认10）
     * @param tenantId        租户ID
     * @return 核保VO分页结果
     */
    @GetMapping("/search")
    public ResponseEntity<Page<UnderwritingVO>> searchUnderwritings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String underwritingType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        UnderwritingEnum.UnderwritingStatus statusEnum =
                BaseEnum.fromCode(UnderwritingEnum.UnderwritingStatus.class, status);
        UnderwritingEnum.UnderwritingType typeEnum =
                UnderwritingEnum.UnderwritingType.fromCode(underwritingType);
        UnderwritingEnum.RiskLevel riskLevelEnum =
                UnderwritingEnum.RiskLevel.fromCode(riskLevel);
        Page<UnderwritingQueryResult> results = underwritingQueryAppService.findUnderwritingsByMultipleConditions(
                statusEnum, typeEnum, riskLevelEnum, null, null, startTime, endTime,
                PageRequest.of(page, size), tenantId);
        return ResponseEntity.ok(results.map(underwritingWebMapper::toVO));
    }

    /**
     * 待核保任务列表（核保员工作台入口）
     *
     * @param underwriterId 核保员ID（可选，不传则返回所有待处理任务）
     * @param page          页码（默认0）
     * @param size          每页大小（默认10）
     * @param tenantId      租户ID
     * @return 待处理核保VO分页结果
     */
    @GetMapping("/pending")
    public ResponseEntity<Page<UnderwritingVO>> getPendingUnderwritingTasks(
            @RequestParam(required = false) String underwriterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        Page<UnderwritingQueryResult> results = underwritingQueryAppService.findPendingUnderwritingTasks(
                underwriterId, null, PageRequest.of(page, size), tenantId);
        return ResponseEntity.ok(results.map(underwritingWebMapper::toVO));
    }

    /**
     * 核保统计数据（管理后台看板聚合用）
     * <p>
     * 返回核保总量、各结论类型数量、待核保数、通过率等指标。
     * 不传时间范围则统计全部。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 核保统计结果
     */
    @GetMapping("/statistics")
    public ResponseEntity<UnderwritingStatisticsResponse> getStatistics(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        UnderwritingStatisticsResult result = underwritingQueryAppService
                .getUnderwritingStatistics(null, null, tenantId);
        return ResponseEntity.ok(underwritingStatisticsWebMapper.toResponse(result));
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
