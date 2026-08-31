package com.titanium.underwriting.web.assembler;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.common.enums.VehicleUsageType;
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

/**
 * 核保 Web 层命令装配器（业务决策型组装）
 * <p>
 * 边界输入（HTTP {@code DTO} / 远程 {@code Request}）到 CQRS 命令的装配枢纽。因装配过程含领域工厂调用
 * （{@link UnderwritingId#generate()}、{@link PolicyId#of(String)}）、值对象构建（{@code BigDecimal}+币种 →
 * {@link UnderwritingAmount}、分块判空 → {@link UnderwritingInput}）与类型转换（{@code code} →
 * {@link UnderwritingEnum.AuditType}），属含业务决策的对象组装（红线 21），故从 MapStruct 映射器中剥离为本
 * 装配器；{@code UnderwritingWebMapper} 只保留纯声明式的同名字段映射。
 * </p>
 */
@Component
public class UnderwritingWebAssembler {

    // ========== HTTP Request（web DTO）→ 领域命令（Controller 用） ==========

    /**
     * 创建核保请求 → 创建核保命令
     *
     * @param request  创建核保请求（web）
     * @param tenantId 租户ID（请求头）
     * @return 创建核保命令
     */
    public CreateUnderwritingCommand toCommand(CreateUnderwritingDTO request, String tenantId) {
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
    public UnderwriteCommand toCommand(String underwritingId, UnderwriteDTO request, String tenantId) {
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
    public SubmitUnderwritingInputCommand toCommand(String underwritingId, SubmitUnderwritingInputDTO request,
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
    public DecideUnderwritingCommand toCommand(String underwritingId, DecideUnderwritingDTO request,
                                               String tenantId) {
        // surchargeAcceptable 由 application 层依产品核保配置填充（UW-4），web 侧置 null
        return new DecideUnderwritingCommand(new UnderwritingId(underwritingId), toAuditType(request.getAuditType()),
                request.getDecidedBy(), tenantId, null);
    }

    // ========== 远程 DTO（api 契约）→ 领域命令（Provider 用） ==========

    /**
     * 远程创建核保请求 → 创建核保命令（Provider 用）
     *
     * @param request  创建核保请求（api 契约）
     * @param tenantId 租户ID（请求头）
     * @return 创建核保命令
     */
    public CreateUnderwritingCommand toCommand(CreateUnderwritingRequest request, String tenantId) {
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
    public UnderwriteCommand toCommand(String underwritingId, UnderwriteRequest request, String tenantId) {
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
    public SubmitUnderwritingInputCommand toCommand(String underwritingId,
            SubmitUnderwritingInputApiRequest request, String tenantId) {
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
    public DecideUnderwritingCommand toCommand(String underwritingId, DecideUnderwritingApiRequest request,
            String tenantId) {
        // surchargeAcceptable 由 application 层依产品核保配置填充（UW-4），web 侧置 null
        return new DecideUnderwritingCommand(new UnderwritingId(underwritingId), toAuditType(request.getAuditType()),
                request.getDecidedBy(), tenantId, null);
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
    public UnderwritingInput toInput(SubmitUnderwritingInputDTO request) {
        return UnderwritingInput.builder().healthDeclaration(toHealthDeclaration(request.getHealthDeclaration()))
                .physicalExamResult(toPhysicalExamResult(request.getPhysicalExamResult()))
                .occupationInfo(toOccupationInfo(request.getOccupationInfo()))
                .vehicleRiskInfo(toVehicleRiskInfo(request.getVehicleRiskInfo())).build();
    }

    /** 远程 api 输入请求 → 核保输入容器值对象（含财务评估，供富核保决策） */
    public UnderwritingInput toApiInput(SubmitUnderwritingInputApiRequest request) {
        return UnderwritingInput.builder()
                .healthDeclaration(toApiHealth(request.getHealthDeclaration()))
                .physicalExamResult(toApiExam(request.getPhysicalExamResult()))
                .occupationInfo(toApiOccupation(request.getOccupationInfo()))
                .financialAssessment(toApiFinancial(request.getFinancialAssessment()))
                .build();
    }

    // ========== 类型转换与归一化 ==========

    /**
     * 核保方式 code → 枚举（缺省回落自动核保）
     *
     * @param auditTypeCode 核保方式 code（AUTOMATIC/MANUAL/HYBRID）
     * @return 核保方式枚举
     */
    public UnderwritingEnum.AuditType toAuditType(String auditTypeCode) {
        if (auditTypeCode == null || auditTypeCode.isBlank()) {
            return UnderwritingEnum.AuditType.AUTOMATIC;
        }
        return UnderwritingEnum.AuditType.valueOf(auditTypeCode);
    }

    /** 空金额表示上游尚未形成标准保费，按零金额进入风险资料核保。 */
    public BigDecimal resolveAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** 空币种沿用跨域契约的人民币默认值。 */
    public CurrencyEnum resolveCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return CurrencyEnum.CNY;
        }
        return CurrencyEnum.requireByCode(currencyCode);
    }

    // ========== 分块装配助手（web DTO → 值对象，空块跳过） ==========

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

    // ========== 分块装配助手（api Request → 值对象，空块/不完整块跳过） ==========

    /** api 健康告知 → 值对象 */
    private HealthDeclaration toApiHealth(SubmitUnderwritingInputApiRequest.HealthDeclarationInput input) {
        if (input == null) {
            return null;
        }
        return new HealthDeclaration(input.getMedicalHistory(), input.getFamilyHistory(), input.isSmoking(),
                input.getHeightCm(), input.getWeightKg());
    }

    /** api 体检 → 值对象 */
    private PhysicalExamResult toApiExam(SubmitUnderwritingInputApiRequest.PhysicalExamInput input) {
        if (input == null || input.getBmi() == null || input.getSystolicPressure() == null
                || input.getDiastolicPressure() == null || input.getBloodGlucose() == null) {
            return null;
        }
        return new PhysicalExamResult(input.getBmi(), input.getSystolicPressure(), input.getDiastolicPressure(),
                input.getBloodGlucose(), input.getAbnormalItems());
    }

    /** api 职业 → 值对象 */
    private OccupationInfo toApiOccupation(SubmitUnderwritingInputApiRequest.OccupationInput input) {
        if (input == null || input.getOccupationName() == null || input.getOccupationName().isBlank()
                || input.getOccupationCategory() < 1 || input.getRiskFactor() == null) {
            return null;
        }
        return new OccupationInfo(input.getOccupationName(), input.getOccupationCategory(), input.getRiskFactor());
    }

    /** api 财务评估 → 值对象 */
    private FinancialAssessment toApiFinancial(SubmitUnderwritingInputApiRequest.FinancialAssessInput input) {
        if (input == null || input.getAnnualIncome() == null || input.getNetWorth() == null
                || input.getRequestedSumInsured() == null) {
            return null;
        }
        return new FinancialAssessment(input.getAnnualIncome(), input.getNetWorth(), input.getRequestedSumInsured(),
                input.getIncomeSource());
    }
}
