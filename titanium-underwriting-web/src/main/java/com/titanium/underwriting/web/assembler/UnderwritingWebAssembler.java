package com.titanium.underwriting.web.assembler;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.request.underwriting.AutoDecideUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.ManualReviewRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.underwriting.UnderwriteRequest;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.common.enums.VehicleUsageType;
import com.titanium.underwriting.valueobject.AutoDecideRequest;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.FinancialAssessment;
import com.titanium.underwriting.valueobject.HealthDeclaration;
import com.titanium.underwriting.valueobject.InsuranceId;
import com.titanium.underwriting.valueobject.InsuredRiskFactors;
import com.titanium.underwriting.valueobject.OccupationInfo;
import com.titanium.underwriting.valueobject.PhysicalExamResult;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.valueobject.VehicleRiskInfo;
import com.titanium.underwriting.web.dto.CreateUnderwritingDTO;
import com.titanium.underwriting.web.dto.DecideUnderwritingDTO;
import com.titanium.underwriting.web.dto.ManualReviewDTO;
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

    /**
     * 系统自动核保主体（操作人缺省值）
     * <p>
     * 🔴 与 policy 域出单适配器的系统主体字面量逐字一致，是跨域审计口径，不得改名（见
     * {@link #resolveOperatorId}）。
     * </p>
     */
    private static final String SYSTEM_OPERATOR = "SYSTEM_AUTO_UNDERWRITING";

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
                request.getUnderwritingType(), request.getRequestBy(), tenantId, request.getProductCode(), null, null);
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
        // manualReviewAmountThreshold 由 application 层金额路由编排器依产品核保配置充实（g02-02），
        // web 侧置 null——命令在编排器派发前必经其充实，web 直连不构成合法路径（同 DecideUnderwritingDTO 先例）
        return new UnderwriteCommand(new UnderwritingId(underwritingId), new UnderwritingAmount(request.getAmount()),
                request.getReason(), request.getUnderwriteBy(), tenantId, null);
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
        // surchargeAcceptable/ruleDecision/configSource 由 application 层编排器依产品核保配置与规则集执行结果填充
        // （dev-505 / m11-1404），web 侧置 null——命令在编排器派发前必经其充实，web 直连不构成合法路径
        return new DecideUnderwritingCommand(new UnderwritingId(underwritingId), toAuditType(request.getAuditType()),
                request.getDecidedBy(), tenantId, null, null, null);
    }

    /**
     * 转人工审核请求 → 转人工命令（Controller 用，g02-03）
     *
     * @param underwritingId 核保ID（path）
     * @param request        转人工审核请求（web）
     * @param tenantId       租户ID（请求头）
     * @return 转人工命令
     */
    public ManualReviewCommand toCommand(String underwritingId, ManualReviewDTO request, String tenantId) {
        return new ManualReviewCommand(new UnderwritingId(underwritingId), request.getReviewComments(),
                request.getReviewedBy(), tenantId);
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
        // 四步建单契约不承载投保单号维度（无该字段），insuranceId 置 null——幂等键由 :auto-decide 端点承担
        return new CreateUnderwritingCommand(UnderwritingId.generate(), PolicyId.of(request.getPolicyId()),
                CustomerId.of(request.getCustomerId()), UnderwritingAmount.of(resolveAmount(request.getAmount()), currency),
                request.getUnderwritingType(), request.getRequestBy(), tenantId, request.getProductCode(), null, null);
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
        // 同 web 侧：阈值由 application 层金额路由编排器依产品核保配置充实（g02-02），契约侧不承载该字段
        return new UnderwriteCommand(new UnderwritingId(underwritingId), new UnderwritingAmount(request.getAmount()),
                request.getReason(), request.getUnderwriteBy(), tenantId, null);
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
        // surchargeAcceptable/ruleDecision/configSource 由 application 层编排器依产品核保配置与规则集执行结果填充
        // （dev-505 / m11-1404），web 侧置 null——命令在编排器派发前必经其充实，provider 直连不构成合法路径
        return new DecideUnderwritingCommand(new UnderwritingId(underwritingId), toAuditType(request.getAuditType()),
                request.getDecidedBy(), tenantId, null, null, null);
    }

    /**
     * 远程转人工审核请求 → 转人工命令（Provider 用，g02-03）
     *
     * @param underwritingId 核保ID（path）
     * @param request        转人工审核请求（api 契约）
     * @param tenantId       租户ID（请求头）
     * @return 转人工命令
     */
    public ManualReviewCommand toCommand(String underwritingId, ManualReviewRequest request, String tenantId) {
        return new ManualReviewCommand(new UnderwritingId(underwritingId), request.getReviewComments(),
                request.getReviewedBy(), tenantId);
    }

    /**
     * 远程自动决策请求 → 自动决策请求值对象（Provider 用，g02-04）
     * <p>
     * 粗粒度端点的入参装配：投保单号为幂等键，风险要素四项<b>可全空</b>（全空时按「未提供任何要素」
     * 传 {@code null}，不构造空壳容器，避免事件载荷噪声——同 {@link #toApiInsuredRiskFactors} 口径）。
     * </p>
     * <p>
     * 🔴 与四步建单契约的差别：那条链路的风险要素藏在 {@code SubmitUnderwritingInputApiRequest} 的
     * {@code occupationInfo}/{@code physicalExamResult} 明细子块里，而明细块守卫要求「字段齐全」，
     * 出单链路只提供职业类别与 BMI 时整块被丢弃。本契约把四项要素<b>提到顶层</b>，
     * 不再借道明细块（G02/AC-01 的成因对策）。
     * </p>
     *
     * @param request  自动决策请求（api 契约）
     * @param tenantId 租户ID（请求头）
     * @return 自动决策请求值对象
     */
    public AutoDecideRequest toAutoDecideRequest(AutoDecideUnderwritingRequest request, String tenantId) {
        InsuredRiskFactors factors = new InsuredRiskFactors(request.getAge(), request.getGender(),
                request.getOccupationCategory(), request.getBmi());
        return new AutoDecideRequest(InsuranceId.of(request.getInsuranceId()),
                CustomerId.of(request.getCustomerId()),
                UnderwritingAmount.of(resolveAmount(request.getAmount()), resolveCurrency(request.getCurrency())),
                resolveUnderwritingType(request.getUnderwritingType()), request.getProductCode(),
                factors.hasAny() ? factors : null, resolveOperatorId(request.getOperatorId()), tenantId);
    }

    // ========== 类型转换与归一化 ==========

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
                .insuredRiskFactors(toApiInsuredRiskFactors(request))
                .build();
    }

    /**
     * api 请求 → 被保人粗粒度风险要素（G02/AC-01）
     * <p>
     * 🔴 四项要素<b>直接取自请求本身</b>，而不是从上面已装配的明细块回填：明细块的守卫要求
     * 字段齐全（如体检须血压/血糖俱全、职业须名称与危险系数俱全），出单链路只提供
     * 职业类别与 BMI 时整个明细块会被丢弃，若从明细块回填粗粒度要素，要素会随之一起丢失
     * ——这正是改造前「职业+BMI 从未真正到达核保域」的成因。
     * </p>
     *
     * @param request api 提交核保输入请求
     * @return 粗粒度风险要素；四项均未提供时返回 {@code null}（不构造空壳，避免事件载荷噪声）
     */
    private InsuredRiskFactors toApiInsuredRiskFactors(SubmitUnderwritingInputApiRequest request) {
        // 职业类别在 api 子块里是 int 基本类型，未设置时为 0；合法类别为 1-6，故以 >=1 判「已提供」
        Integer occupationCategory = request.getOccupationInfo() != null
                && request.getOccupationInfo().getOccupationCategory() >= 1
                        ? request.getOccupationInfo().getOccupationCategory()
                        : null;
        BigDecimal bmi = request.getPhysicalExamResult() != null ? request.getPhysicalExamResult().getBmi() : null;
        InsuredRiskFactors factors = new InsuredRiskFactors(request.getAge(), request.getGender(),
                occupationCategory, bmi);
        return factors.hasAny() ? factors : null;
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

    /**
     * 核保类型缺省回落新单核保
     * <p>
     * 粗粒度端点的调用方是出单主链路，其业务语义恒为「新投保单的首次核保」，故缺省即
     * {@link UnderwritingEnum.UnderwritingType#NEW_BUSINESS}；续保/批改/复效场景由调用方显式声明。
     * </p>
     *
     * @param underwritingType 核保类型（可为空）
     * @return 核保类型枚举
     */
    public UnderwritingEnum.UnderwritingType resolveUnderwritingType(
            UnderwritingEnum.UnderwritingType underwritingType) {
        return underwritingType == null ? UnderwritingEnum.UnderwritingType.NEW_BUSINESS : underwritingType;
    }

    /**
     * 操作人缺省回落系统主体
     * <p>
     * 🔴 字面量与 policy 域出单适配器的系统主体**逐字一致**（{@code SYSTEM_AUTO_UNDERWRITING}）：
     * 该值经核保事件流的 {@code decidedBy} 固化，是决策审计里「这是系统自动决策、非某位核保员所为」
     * 的唯一标记，也是跨域对账口径，一经发布不可改名。
     * </p>
     * <p>
     * ⚠️ 刻意<b>不</b>回落为请求上下文里的操作人：核保决策的主体应是发起决策的系统链路，
     * 而非恰好点击了出单按钮的柜员（柜员信息经投保单侧的审计字段留痕）。
     * </p>
     *
     * @param operatorId 上游显式声明的操作人（可为空）
     * @return 操作人标识
     */
    public String resolveOperatorId(String operatorId) {
        return operatorId == null || operatorId.isBlank() ? SYSTEM_OPERATOR : operatorId;
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
