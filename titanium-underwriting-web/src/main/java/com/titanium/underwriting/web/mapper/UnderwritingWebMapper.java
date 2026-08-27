package com.titanium.underwriting.web.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.response.UnderwritingResponse;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.common.enums.VehicleUsageType;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.FinancialAssessment;
import com.titanium.underwriting.valueobject.HealthDeclaration;
import com.titanium.underwriting.valueobject.OccupationInfo;
import com.titanium.underwriting.valueobject.PhysicalExamResult;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.valueobject.VehicleRiskInfo;
import com.titanium.underwriting.web.dto.CreateUnderwritingDTO;
import com.titanium.underwriting.web.dto.DecideUnderwritingDTO;
import com.titanium.underwriting.web.dto.SubmitUnderwritingInputDTO;
import com.titanium.underwriting.web.dto.UnderwriteDTO;
import com.titanium.underwriting.web.vo.UnderwritingVO;

/**
 * 核保 Web 层对象映射器（MapStruct）
 * <p>
 * 边界输入到 CQRS 命令的转换枢纽：HTTP {@code Request}（Controller 用）与远程 {@code DTO}（Provider 用）
 * → 领域命令；读模型结果 {@code UnderwritingQueryResult} → 展示 {@code VO}（Controller 用）/ 对外
 * {@code UnderwritingResponse}（Provider 用）。application 门面入参即领域命令，本映射器在 web 层完成
 * Request/DTO → Command 的结构翻译与值对象装配（{@code BigDecimal}+币种 → {@link UnderwritingAmount}、
 * 险种输入 → {@link UnderwritingInput}）。因命令承载带校验的值对象、险种输入需分块判空，命令构造采用
 * default 方法手写装配，读模型→VO/DTO 的同名字段映射交由 MapStruct 生成。
 * </p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UnderwritingWebMapper {

    // ========== 读模型结果 → 展示/契约对象（MapStruct 同名映射） ==========

    /**
     * 读模型结果 → 展示 VO（Controller 用）
     *
     * @param result 读侧查询结果
     * @return 核保 VO
     */
    UnderwritingVO toVO(UnderwritingQueryResult result);

    /**
     * 读模型结果 → 对外 Response（Provider 用）
     *
     * @param result 读侧查询结果
     * @return 核保 Response
     */
    UnderwritingResponse toResponse(UnderwritingQueryResult result);

    /**
     * Response → VO（Provider 结果透传给人机终端时复用）
     *
     * @param response 核保 Response
     * @return 核保 VO
     */
    UnderwritingVO toVO(UnderwritingResponse response);

    /**
     * Response 列表 → VO 列表
     *
     * @param responseList 核保 Response 列表
     * @return 核保 VO 列表
     */
    List<UnderwritingVO> toVOList(List<UnderwritingResponse> responseList);

    // ========== HTTP Request → 领域命令（Controller 用） ==========

    /**
     * 创建核保请求 → 创建核保命令
     *
     * @param request  创建核保请求（web）
     * @param tenantId 租户ID（请求头）
     * @return 创建核保命令
     */
    default CreateUnderwritingCommand toCommand(CreateUnderwritingDTO request, String tenantId) {
        // 边界防腐：REST 入参币种 code(String) 转 CurrencyEnum，装配为带校验的金额值对象
        CurrencyEnum currency = resolveCurrency(request.getCurrency());
        return new CreateUnderwritingCommand(UnderwritingId.generate(), PolicyId.of(request.getPolicyId()),
                CustomerId.of(request.getCustomerId()), UnderwritingAmount.of(resolveAmount(request.getAmount()), currency),
                request.getUnderwritingType(), request.getRequestBy(), tenantId, request.getProductCode());
    }

    /**
     * 执行核保请求 → 执行核保命令
     *
     * @param underwritingId 核保ID（path）
     * @param request        执行核保请求（web）
     * @param tenantId       租户ID（请求头）
     * @return 执行核保命令
     */
    default UnderwriteCommand toCommand(String underwritingId, UnderwriteDTO request, String tenantId) {
        return new UnderwriteCommand(new UnderwritingId(underwritingId), new UnderwritingAmount(request.getAmount()),
                request.getReason(), request.getUnderwriteBy(), tenantId);
    }

    /**
     * 提交核保输入请求 → 提交核保输入命令
     *
     * @param underwritingId 核保ID（path）
     * @param request        提交核保输入请求（web）
     * @param tenantId       租户ID（请求头）
     * @return 提交核保输入命令
     */
    default SubmitUnderwritingInputCommand toCommand(String underwritingId, SubmitUnderwritingInputDTO request,
                                                     String tenantId) {
        return new SubmitUnderwritingInputCommand(new UnderwritingId(underwritingId), toInput(request),
                request.getSubmittedBy(), tenantId);
    }

    /**
     * 核保决策请求 → 核保决策命令
     *
     * @param underwritingId 核保ID（path）
     * @param request        核保决策请求（web）
     * @param tenantId       租户ID（请求头）
     * @return 核保决策命令
     */
    default DecideUnderwritingCommand toCommand(String underwritingId, DecideUnderwritingDTO request,
                                                String tenantId) {
        // surchargeAcceptable 由 application 层依产品核保配置填充（UW-4），web 侧置 null
        return new DecideUnderwritingCommand(new UnderwritingId(underwritingId), toAuditType(request.getAuditType()),
                request.getDecidedBy(), tenantId, null);
    }

    // ========== 远程 DTO → 领域命令（Provider 用） ==========

    /**
     * 远程创建核保请求 → 创建核保命令（Provider 用）
     *
     * @param request  创建核保请求（api 契约）
     * @param tenantId 租户ID（请求头）
     * @return 创建核保命令
     */
    default CreateUnderwritingCommand toCommand(
            com.titanium.underwriting.api.request.CreateUnderwritingRequest request, String tenantId) {
        CurrencyEnum currency = resolveCurrency(request.getCurrency());
        return new CreateUnderwritingCommand(UnderwritingId.generate(), PolicyId.of(request.getPolicyId()),
                CustomerId.of(request.getCustomerId()), UnderwritingAmount.of(resolveAmount(request.getAmount()), currency),
                request.getUnderwritingType(), request.getRequestBy(), tenantId, request.getProductCode());
    }

    /**
     * 远程执行核保请求 → 执行核保命令（Provider 用）
     *
     * @param underwritingId 核保ID（path）
     * @param request        执行核保请求（api 契约）
     * @param tenantId       租户ID（请求头）
     * @return 执行核保命令
     */
    default UnderwriteCommand toCommand(String underwritingId,
                                        com.titanium.underwriting.api.request.UnderwriteRequest request,
                                        String tenantId) {
        return new UnderwriteCommand(new UnderwritingId(underwritingId), new UnderwritingAmount(request.getAmount()),
                request.getReason(), request.getUnderwriteBy(), tenantId);
    }

    /**
     * 远程提交核保输入请求 → 提交核保输入命令（Provider 用，富核保路径）
     *
     * @param underwritingId 核保ID（path）
     * @param request        提交核保输入请求（api 契约）
     * @param tenantId       租户ID（请求头）
     * @return 提交核保输入命令
     */
    default SubmitUnderwritingInputCommand toCommand(String underwritingId,
            com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest request, String tenantId) {
        return new SubmitUnderwritingInputCommand(new UnderwritingId(underwritingId), toApiInput(request),
                request.getSubmittedBy(), tenantId);
    }

    /**
     * 远程核保决策请求 → 核保决策命令（Provider 用，富核保路径）
     *
     * @param underwritingId 核保ID（path）
     * @param request        核保决策请求（api 契约）
     * @param tenantId       租户ID（请求头）
     * @return 核保决策命令
     */
    default DecideUnderwritingCommand toCommand(String underwritingId,
            com.titanium.underwriting.api.request.DecideUnderwritingApiRequest request, String tenantId) {
        // surchargeAcceptable 由 application 层依产品核保配置填充（UW-4），web 侧置 null
        return new DecideUnderwritingCommand(new UnderwritingId(underwritingId), toAuditType(request.getAuditType()),
                request.getDecidedBy(), tenantId, null);
    }

    /** 远程 api 输入请求 → 核保输入容器值对象（含财务评估，供富核保决策） */
    default UnderwritingInput toApiInput(
            com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest request) {
        return UnderwritingInput.builder()
                .healthDeclaration(toApiHealth(request.getHealthDeclaration()))
                .physicalExamResult(toApiExam(request.getPhysicalExamResult()))
                .occupationInfo(toApiOccupation(request.getOccupationInfo()))
                .financialAssessment(toApiFinancial(request.getFinancialAssessment()))
                .build();
    }

    /** api 健康告知 → 值对象 */
    private HealthDeclaration toApiHealth(
            com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest.HealthDeclarationInput input) {
        if (input == null) {
            return null;
        }
        return new HealthDeclaration(input.getMedicalHistory(), input.getFamilyHistory(), input.isSmoking(),
                input.getHeightCm(), input.getWeightKg());
    }

    /** api 体检 → 值对象 */
    private PhysicalExamResult toApiExam(
            com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest.PhysicalExamInput input) {
        if (input == null || input.getBmi() == null || input.getSystolicPressure() == null
                || input.getDiastolicPressure() == null || input.getBloodGlucose() == null) {
            return null;
        }
        return new PhysicalExamResult(input.getBmi(), input.getSystolicPressure(), input.getDiastolicPressure(),
                input.getBloodGlucose(), input.getAbnormalItems());
    }

    /** api 职业 → 值对象 */
    private OccupationInfo toApiOccupation(
            com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest.OccupationInput input) {
        if (input == null || input.getOccupationName() == null || input.getOccupationName().isBlank()
                || input.getOccupationCategory() < 1 || input.getRiskFactor() == null) {
            return null;
        }
        return new OccupationInfo(input.getOccupationName(), input.getOccupationCategory(), input.getRiskFactor());
    }

    /** api 财务评估 → 值对象 */
    private FinancialAssessment toApiFinancial(
            com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest.FinancialAssessInput input) {
        if (input == null || input.getAnnualIncome() == null || input.getNetWorth() == null
                || input.getRequestedSumInsured() == null) {
            return null;
        }
        return new FinancialAssessment(input.getAnnualIncome(), input.getNetWorth(), input.getRequestedSumInsured(),
                input.getIncomeSource());
    }

    // ========== 险种输入装配（分块判空 + 值对象内聚校验） ==========

    /**
     * 提交核保输入请求 → 核保输入容器值对象
     * <p>
     * 四类输入按险种可选填充，未填充的整块保持 {@code null}；各值对象构造器内聚校验，此处仅做分块装配。
     * </p>
     *
     * @param request 提交核保输入请求（web）
     * @return 核保输入容器值对象
     */
    default UnderwritingInput toInput(SubmitUnderwritingInputDTO request) {
        return UnderwritingInput.builder().healthDeclaration(toHealthDeclaration(request.getHealthDeclaration()))
                .physicalExamResult(toPhysicalExamResult(request.getPhysicalExamResult()))
                .occupationInfo(toOccupationInfo(request.getOccupationInfo()))
                .vehicleRiskInfo(toVehicleRiskInfo(request.getVehicleRiskInfo())).build();
    }

    /**
     * 核保方式 code → 枚举（缺省回落自动核保）
     *
     * @param auditTypeCode 核保方式 code（AUTOMATIC/MANUAL/HYBRID）
     * @return 核保方式枚举
     */
    default UnderwritingEnum.AuditType toAuditType(String auditTypeCode) {
        if (auditTypeCode == null || auditTypeCode.isBlank()) {
            return UnderwritingEnum.AuditType.AUTOMATIC;
        }
        return UnderwritingEnum.AuditType.valueOf(auditTypeCode);
    }

    /** 空金额表示上游尚未形成标准保费，按零金额进入风险资料核保。 */
    default BigDecimal resolveAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** 空币种沿用跨域契约的人民币默认值。 */
    default CurrencyEnum resolveCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return CurrencyEnum.CNY;
        }
        return CurrencyEnum.requireByCode(currencyCode);
    }

    /** 创建命令同步快照，不依赖异步读模型。 */
    default UnderwritingResponse toResponse(CreateUnderwritingCommand command) {
        UnderwritingResponse response = new UnderwritingResponse();
        response.setUnderwritingId(command.underwritingId().value());
        response.setPolicyId(command.policyId().value());
        response.setCustomerId(command.customerId().value());
        response.setAmount(command.amount().amount());
        response.setUnderwritingType(command.underwritingType());
        response.setStatus(UnderwritingEnum.UnderwritingStatus.PENDING);
        response.setCreatedBy(command.createdBy());
        response.setTenantId(command.tenantId());
        return response;
    }

    /** 状态命令同步快照，不依赖异步读模型。 */
    default UnderwritingResponse toResponse(UnderwritingStatusChangedEvent event) {
        UnderwritingResponse response = new UnderwritingResponse();
        response.setUnderwritingId(event.underwritingId().value());
        response.setStatus(event.newStatus());
        if (event.newStatus() == UnderwritingEnum.UnderwritingStatus.REJECTED
                || event.newStatus() == UnderwritingEnum.UnderwritingStatus.DECLINED) {
            response.setRejectReason(event.reason());
        } else {
            response.setReviewComments(event.reason());
        }
        response.setUpdatedBy(event.changedBy());
        response.setTenantId(event.tenantId());
        return response;
    }

    /** 输入提交命令同步快照，不依赖异步读模型。 */
    default UnderwritingResponse toResponse(UnderwritingInputSubmittedEvent event) {
        UnderwritingResponse response = new UnderwritingResponse();
        response.setUnderwritingId(event.underwritingId().value());
        response.setStatus(UnderwritingEnum.UnderwritingStatus.PENDING);
        response.setUpdatedBy(event.submittedBy());
        response.setTenantId(event.tenantId());
        return response;
    }

    /** 最终决策命令同步快照，为调用方提供权威核保结论。 */
    default UnderwritingResponse toResponse(UnderwritingDecidedEvent event) {
        UnderwritingResponse response = new UnderwritingResponse();
        response.setUnderwritingId(event.underwritingId().value());
        response.setPolicyId(event.policyId().value());
        response.setRiskLevel(event.riskLevel());
        response.setConclusionType(event.conclusionType());
        response.setAuditType(event.auditType());
        response.setStatus(event.newStatus());
        response.setUpdatedBy(event.decidedBy());
        response.setTenantId(event.tenantId());
        if (event.extraPremium() != null) {
            response.setExtraPremiumType(event.extraPremium().type() != null
                    ? event.extraPremium().type().getCode()
                    : null);
            response.setExtraPremiumRatio(event.extraPremium().ratio());
            response.setExtraPremiumFixedAmount(event.extraPremium().fixedAmount());
            response.setSurchargeReason(event.extraPremium().reason());
        }
        return response;
    }

    /** 健康告知输入装配（空块跳过） */
    private HealthDeclaration toHealthDeclaration(SubmitUnderwritingInputDTO.HealthDeclarationInput input) {
        if (input == null) {
            return null;
        }
        return new HealthDeclaration(input.getMedicalHistory(), input.getFamilyHistory(), input.isSmoking(),
                input.getHeightCm(), input.getWeightKg());
    }

    /** 体检报告输入装配（空块跳过） */
    private PhysicalExamResult toPhysicalExamResult(SubmitUnderwritingInputDTO.PhysicalExamInput input) {
        if (input == null) {
            return null;
        }
        return new PhysicalExamResult(input.getBmi(), input.getSystolicPressure(), input.getDiastolicPressure(),
                input.getBloodGlucose(), input.getAbnormalItems());
    }

    /** 职业信息输入装配（空块跳过） */
    private OccupationInfo toOccupationInfo(SubmitUnderwritingInputDTO.OccupationInput input) {
        if (input == null) {
            return null;
        }
        return new OccupationInfo(input.getOccupationName(), input.getOccupationCategory(), input.getRiskFactor());
    }

    /** 车辆风险输入装配（空块跳过，使用性质 code 转枚举） */
    private VehicleRiskInfo toVehicleRiskInfo(SubmitUnderwritingInputDTO.VehicleRiskInput input) {
        if (input == null) {
            return null;
        }
        return new VehicleRiskInfo(input.getVehicleAgeYears(), VehicleUsageType.valueOf(input.getUsageNature()),
                input.getHistoricalClaimCount(), input.getNcdFactor());
    }
}
