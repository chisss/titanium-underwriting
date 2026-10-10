package com.titanium.underwriting.web.assembler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.underwriting.HealthDeclarationQuestion;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.request.underwriting.AutoDecideUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.common.enums.VehicleUsageType;
import com.titanium.underwriting.exception.UnderwritingValidationException;
import com.titanium.underwriting.valueobject.AutoDecideRequest;
import com.titanium.underwriting.valueobject.HealthDeclaration;
import com.titanium.underwriting.valueobject.HealthDeclarationAnswer;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.web.dto.SubmitUnderwritingInputDTO;

/**
 * UnderwritingWebAssembler 单元测试
 * <p>
 * 覆盖边界输入→领域命令的业务决策型装配：险种输入分块判空装配、核保方式 code 解析、
 * 空金额/空币种归一化与跨域风险输入的空安全装配（红线 21：>5 字段命令组装内聚于装配器）。
 * </p>
 */
class UnderwritingWebAssemblerTest {

    private final UnderwritingWebAssembler assembler = new UnderwritingWebAssembler();

    @Test
    @DisplayName("仅填车辆风险时，其余三块为 null，使用性质 code 转枚举")
    void shouldAssembleOnlyVehicleSection() {
        SubmitUnderwritingInputDTO request = new SubmitUnderwritingInputDTO();
        SubmitUnderwritingInputDTO.VehicleRiskInput vehicle = new SubmitUnderwritingInputDTO.VehicleRiskInput();
        vehicle.setVehicleAgeYears(3);
        vehicle.setUsageNature("FAMILY");
        vehicle.setHistoricalClaimCount(1);
        vehicle.setNcdFactor(new BigDecimal("0.85"));
        request.setVehicleRiskInfo(vehicle);

        UnderwritingInput input = assembler.toInput(request);

        assertNull(input.healthDeclaration());
        assertNull(input.physicalExamResult());
        assertNull(input.occupationInfo());
        assertNotNull(input.vehicleRiskInfo());
        assertEquals(VehicleUsageType.FAMILY, input.vehicleRiskInfo().usageNature());
        assertEquals(3, input.vehicleRiskInfo().vehicleAgeYears());
    }

    @Test
    @DisplayName("健康告知装配：身高体重正确传入值对象")
    void shouldAssembleHealthDeclaration() {
        SubmitUnderwritingInputDTO request = new SubmitUnderwritingInputDTO();
        SubmitUnderwritingInputDTO.HealthDeclarationInput health =
                new SubmitUnderwritingInputDTO.HealthDeclarationInput();
        health.setHeightCm(new BigDecimal("175"));
        health.setWeightKg(new BigDecimal("70"));
        health.setSmoking(false);
        request.setHealthDeclaration(health);

        UnderwritingInput input = assembler.toInput(request);

        assertNotNull(input.healthDeclaration());
        assertEquals(new BigDecimal("175"), input.healthDeclaration().heightCm());
    }

    @Test
    @DisplayName("核保方式：null/空回落为 AUTOMATIC，有值按 code 解析")
    void shouldResolveAuditType() {
        assertEquals(UnderwritingEnum.AuditType.AUTOMATIC, assembler.toAuditType(null));
        assertEquals(UnderwritingEnum.AuditType.AUTOMATIC, assembler.toAuditType(""));
        assertEquals(UnderwritingEnum.AuditType.MANUAL, assembler.toAuditType("MANUAL"));
        assertEquals(UnderwritingEnum.AuditType.HYBRID, assembler.toAuditType("HYBRID"));
    }

    @Test
    @DisplayName("跨域创建请求：空金额和空币种按标准核保合同归一为 0 CNY")
    void shouldNormalizeMissingAmountAndCurrency() {
        CreateUnderwritingRequest request = new CreateUnderwritingRequest();
        request.setPolicyId("POL-001");
        request.setCustomerId("CUS-001");
        request.setUnderwritingType(UnderwritingEnum.UnderwritingType.NEW_BUSINESS);
        request.setRequestBy("system");

        CreateUnderwritingCommand command = assembler.toCommand(request, "TENANT-001");

        assertEquals(new BigDecimal("0.00"), command.amount().amount());
        assertEquals(CurrencyEnum.CNY, command.amount().currency());
    }

    @Test
    @DisplayName("跨域风险输入：空请求与不完整块均不阻断标准体合同")
    void shouldAcceptEmptyAndPartialRiskInput() {
        SubmitUnderwritingInputApiRequest empty = new SubmitUnderwritingInputApiRequest();
        assertNotNull(assembler.toApiInput(empty));
        assertEquals(false, assembler.toApiInput(empty).hasAnyInput());

        SubmitUnderwritingInputApiRequest partial = new SubmitUnderwritingInputApiRequest();
        SubmitUnderwritingInputApiRequest.PhysicalExamInput exam =
                new SubmitUnderwritingInputApiRequest.PhysicalExamInput();
        exam.setBmi(new BigDecimal("22.0"));
        partial.setPhysicalExamResult(exam);
        SubmitUnderwritingInputApiRequest.OccupationInput occupation =
                new SubmitUnderwritingInputApiRequest.OccupationInput();
        occupation.setOccupationCategory(1);
        partial.setOccupationInfo(occupation);
        SubmitUnderwritingInputApiRequest.FinancialAssessInput financial =
                new SubmitUnderwritingInputApiRequest.FinancialAssessInput();
        financial.setRequestedSumInsured(new BigDecimal("1000"));
        partial.setFinancialAssessment(financial);

        assertDoesNotThrow(() -> assembler.toApiInput(partial));
    }

    @Test
    @DisplayName("G02/AC-01 粗粒度要素直接取自请求本身，不因明细块字段不全而连带丢失")
    void shouldAssembleCoarseRiskFactorsWithoutDependingOnDetailBlocks() {
        SubmitUnderwritingInputApiRequest request = new SubmitUnderwritingInputApiRequest();
        request.setAge(45);
        request.setGender(CustomerGender.MALE);
        // 明细块刻意「不完整」：职业无名称与危险系数、体检无血压血糖——明细块的守卫会整块丢弃
        SubmitUnderwritingInputApiRequest.OccupationInput occupation =
                new SubmitUnderwritingInputApiRequest.OccupationInput();
        occupation.setOccupationCategory(4);
        request.setOccupationInfo(occupation);
        SubmitUnderwritingInputApiRequest.PhysicalExamInput exam =
                new SubmitUnderwritingInputApiRequest.PhysicalExamInput();
        exam.setBmi(new BigDecimal("29"));
        request.setPhysicalExamResult(exam);

        UnderwritingInput input = assembler.toApiInput(request);

        // 明细块确被守卫丢弃（保持 NULL）——这正是改造前「职业+BMI 从未到达核保域」的现场
        assertNull(input.occupationInfo());
        assertNull(input.physicalExamResult());
        // 粗粒度要素独立存活，四项俱全
        assertNotNull(input.insuredRiskFactors());
        assertEquals(Integer.valueOf(45), input.insuredRiskFactors().age());
        assertEquals(CustomerGender.MALE, input.insuredRiskFactors().gender());
        assertEquals(Integer.valueOf(4), input.insuredRiskFactors().occupationCategory());
        assertEquals(new BigDecimal("29"), input.insuredRiskFactors().bmi());
        assertEquals(true, input.hasAnyInput());
    }

    @Test
    @DisplayName("G02/AC-01 四项要素均未提供时不构造空壳值对象")
    void shouldOmitCoarseRiskFactorsWhenNothingProvided() {
        assertNull(assembler.toApiInput(new SubmitUnderwritingInputApiRequest()).insuredRiskFactors());

        // 只带提交人、无任何要素：同样不得构造空壳（避免事件载荷与日志噪声）
        SubmitUnderwritingInputApiRequest onlySubmitter = new SubmitUnderwritingInputApiRequest();
        onlySubmitter.setSubmittedBy("UW_001");
        assertNull(assembler.toApiInput(onlySubmitter).insuredRiskFactors());
        assertEquals(false, assembler.toApiInput(onlySubmitter).hasAnyInput());
    }

    @Test
    @DisplayName("G02/AC-01 职业类别 0 是 api 子块的未设置哨兵值，按「未提供」处理；越界值则失败关闭")
    void shouldDistinguishOccupationSentinelFromIllegalValue() {
        SubmitUnderwritingInputApiRequest request = new SubmitUnderwritingInputApiRequest();
        request.setAge(30);
        SubmitUnderwritingInputApiRequest.OccupationInput occupation =
                new SubmitUnderwritingInputApiRequest.OccupationInput();
        // api 子块里 occupationCategory 是 int 基本类型，未设置时即为 0
        occupation.setOccupationCategory(0);
        request.setOccupationInfo(occupation);

        UnderwritingInput input = assertDoesNotThrow(() -> assembler.toApiInput(request));

        // 0 视为未提供：既不入要素，也不触发值对象校验
        assertNull(input.insuredRiskFactors().occupationCategory());
        assertEquals(Integer.valueOf(30), input.insuredRiskFactors().age());

        // 越界类别（7）不是「未提供」而是非法数据：不得被静默丢弃，须失败关闭暴露上游缺陷
        SubmitUnderwritingInputApiRequest invalid = new SubmitUnderwritingInputApiRequest();
        SubmitUnderwritingInputApiRequest.OccupationInput outOfRange =
                new SubmitUnderwritingInputApiRequest.OccupationInput();
        outOfRange.setOccupationCategory(7);
        invalid.setOccupationInfo(outOfRange);
        assertThrows(UnderwritingValidationException.class, () -> assembler.toApiInput(invalid));
    }

    @Test
    @DisplayName("G02/AC-05 自动决策装配：投保单号作幂等键，四要素提到顶层整体透传")
    void shouldAssembleAutoDecideRequest() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");
        request.setAmount(new BigDecimal("500000"));
        request.setCurrency("CNY");
        request.setProductCode("PRD-001");
        request.setUnderwritingType(UnderwritingEnum.UnderwritingType.NEW_BUSINESS);
        request.setOperatorId("OPERATOR-001");
        request.setAge(45);
        request.setGender(CustomerGender.MALE);
        request.setOccupationCategory(4);
        request.setBmi(new BigDecimal("29"));

        AutoDecideRequest assembled = assembler.toAutoDecideRequest(request, "TENANT-001");

        assertEquals("INS-001", assembled.insuranceId().value());
        assertEquals("CUST-001", assembled.customerId().value());
        assertEquals(0, new BigDecimal("500000").compareTo(assembled.amount().amount()));
        assertEquals(CurrencyEnum.CNY, assembled.amount().currency());
        assertEquals("PRD-001", assembled.productCode());
        assertEquals("TENANT-001", assembled.tenantId());
        assertEquals("OPERATOR-001", assembled.operatorId());
        // 🔴 四要素直接取自顶层：不再借道 occupationInfo/physicalExamResult 明细子块——
        // 明细块的「字段齐全」守卫会把「只有职业类别 + BMI」的出单链路输入整块丢掉（AC-01 成因）
        assertNotNull(assembled.riskFactors());
        assertEquals(Integer.valueOf(45), assembled.riskFactors().age());
        assertEquals(CustomerGender.MALE, assembled.riskFactors().gender());
        assertEquals(Integer.valueOf(4), assembled.riskFactors().occupationCategory());
        assertEquals(new BigDecimal("29"), assembled.riskFactors().bmi());
    }

    @Test
    @DisplayName("G02/AC-05 自动决策装配：未提供要素不构造空壳，核保类型/操作人/币种各按缺省归一")
    void shouldNormalizeAutoDecideDefaultsWithoutBuildingEmptyRiskFactorShell() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");

        AutoDecideRequest assembled = assembler.toAutoDecideRequest(request, "TENANT-001");

        // 四项要素均未提供 ⇒ 传 null 而非空壳（null 与「提供了 0 岁/0 类职业」语义必须可区分）
        assertNull(assembled.riskFactors());
        assertEquals(UnderwritingEnum.UnderwritingType.NEW_BUSINESS, assembled.underwritingType());
        // 无人工操作人时回落系统主体，绝不回落投保人（审计留痕须记真实主体）
        assertEquals("SYSTEM_AUTO_UNDERWRITING", assembled.operatorId());
        assertFalse(assembled.operatorId().contains(request.getCustomerId()));
        assertEquals(CurrencyEnum.CNY, assembled.amount().currency());
        assertEquals(0, BigDecimal.ZERO.compareTo(assembled.amount().amount()));
    }

    @Test
    @DisplayName("G02/AC-05 自动决策装配：仅提供部分要素时只带该部分，不补零")
    void shouldCarryOnlyProvidedRiskFactors() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");
        request.setAge(30);

        AutoDecideRequest assembled = assembler.toAutoDecideRequest(request, "TENANT-001");

        assertNotNull(assembled.riskFactors());
        assertEquals(Integer.valueOf(30), assembled.riskFactors().age());
        assertNull(assembled.riskFactors().gender());
        assertNull(assembled.riskFactors().occupationCategory());
        assertNull(assembled.riskFactors().bmi());
    }

    @Test
    @DisplayName("G12/g12-01 AC-01 自动决策装配：健康告知五项整体透传为值对象")
    void shouldTransmitHealthDeclarationOntoAutoDecideRequest() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");
        AutoDecideUnderwritingRequest.HealthDeclarationInput health =
                new AutoDecideUnderwritingRequest.HealthDeclarationInput();
        health.setMedicalHistory(List.of("高血压"));
        health.setFamilyHistory(List.of("糖尿病家族史"));
        health.setSmoking(true);
        health.setHeightCm(new BigDecimal("175"));
        health.setWeightKg(new BigDecimal("80"));
        request.setHealthDeclaration(health);

        AutoDecideRequest assembled = assembler.toAutoDecideRequest(request, "TENANT-001");

        HealthDeclaration declaration = assembled.healthDeclaration();
        assertNotNull(declaration);
        assertEquals(List.of("高血压"), declaration.medicalHistory());
        assertEquals(List.of("糖尿病家族史"), declaration.familyHistory());
        assertEquals(true, declaration.smoking());
        assertEquals(new BigDecimal("175"), declaration.heightCm());
        assertEquals(new BigDecimal("80"), declaration.weightKg());
    }

    @Test
    @DisplayName("G12/g12-01 AC-01 自动决策装配：未提供告知块时不构造空壳（null 与低风险告知可区分）")
    void shouldKeepHealthDeclarationNullWhenBlockAbsent() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");

        AutoDecideRequest assembled = assembler.toAutoDecideRequest(request, "TENANT-001");

        // 未提供 ⇒ null：构造空壳会把「没告知」读成「告知了无病史、不吸烟」
        assertNull(assembled.healthDeclaration());
    }

    @Test
    @DisplayName("G12/g12-01 AC-01 自动决策装配：声明了告知块却缺吸烟答案 → 显式拒绝而非按不吸烟处理")
    void shouldRejectDeclarationBlockMissingSmokingAnswer() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");
        AutoDecideUnderwritingRequest.HealthDeclarationInput health =
                new AutoDecideUnderwritingRequest.HealthDeclarationInput();
        health.setHeightCm(new BigDecimal("175"));
        health.setWeightKg(new BigDecimal("80"));
        request.setHealthDeclaration(health);

        // 契约侧吸烟用包装类型表达三态，null 即「块存在但必答项缺失」——调用方数据残缺必须显式失败，
        // 静默按 false（不吸烟）处理会反转风险方向
        assertThrows(UnderwritingValidationException.class,
                () -> assembler.toAutoDecideRequest(request, "TENANT-001"));
    }

    @Test
    @DisplayName("G12/g12-02 自动决策装配：自定义告知项答案按编码翻译为值对象，未知编码跳过")
    void shouldTranslateConfiguredHealthAnswersAndSkipUnknownCodes() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");
        AutoDecideUnderwritingRequest.HealthDeclarationInput health =
                new AutoDecideUnderwritingRequest.HealthDeclarationInput();
        health.setHeightCm(new BigDecimal("175"));
        health.setWeightKg(new BigDecimal("80"));
        health.setSmoking(false);
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("HOSPITALIZATION_TWO_YEARS", "true");
        answers.put("SURGERY_HISTORY", "false");
        answers.put("UNKNOWN_QUESTION", "true");
        health.setAnswers(answers);
        request.setHealthDeclaration(health);

        AutoDecideRequest assembled = assembler.toAutoDecideRequest(request, "TENANT-001");

        // 未知编码（对端新增项）在滚动升级窗口内跳过而非整链失败；已知项按原顺序翻译
        List<HealthDeclarationAnswer> parsed = assembled.healthDeclaration().answers();
        assertEquals(2, parsed.size());
        assertEquals(HealthDeclarationQuestion.HOSPITALIZATION_TWO_YEARS, parsed.get(0).question());
        assertEquals("true", parsed.get(0).answer());
        assertEquals(HealthDeclarationQuestion.SURGERY_HISTORY, parsed.get(1).question());
        assertEquals("false", parsed.get(1).answer());
    }

    @Test
    @DisplayName("G12/g12-02 自动决策装配：未提供答案清单时为空列表（不改变既有五要素透传）")
    void shouldKeepConfiguredAnswersEmptyWhenAbsent() {
        AutoDecideUnderwritingRequest request = new AutoDecideUnderwritingRequest();
        request.setInsuranceId("INS-001");
        request.setCustomerId("CUST-001");
        AutoDecideUnderwritingRequest.HealthDeclarationInput health =
                new AutoDecideUnderwritingRequest.HealthDeclarationInput();
        health.setHeightCm(new BigDecimal("175"));
        health.setWeightKg(new BigDecimal("80"));
        health.setSmoking(false);
        request.setHealthDeclaration(health);

        AutoDecideRequest assembled = assembler.toAutoDecideRequest(request, "TENANT-001");

        assertEquals(List.of(), assembled.healthDeclaration().answers());
    }

    @Test
    @DisplayName("G12/g12-02 提交输入装配：web DTO 路径同法翻译答案清单")
    void shouldTranslateConfiguredAnswersFromDtoPath() {
        SubmitUnderwritingInputDTO request = new SubmitUnderwritingInputDTO();
        SubmitUnderwritingInputDTO.HealthDeclarationInput health =
                new SubmitUnderwritingInputDTO.HealthDeclarationInput();
        health.setHeightCm(new BigDecimal("175"));
        health.setWeightKg(new BigDecimal("70"));
        health.setAnswers(Map.of("DRUG_ALLERGY", "true"));
        request.setHealthDeclaration(health);

        UnderwritingInput input = assembler.toInput(request);

        assertEquals(1, input.healthDeclaration().answers().size());
        assertEquals(HealthDeclarationQuestion.DRUG_ALLERGY, input.healthDeclaration().answers().get(0).question());
        assertEquals("true", input.healthDeclaration().answers().get(0).answer());
    }
}
