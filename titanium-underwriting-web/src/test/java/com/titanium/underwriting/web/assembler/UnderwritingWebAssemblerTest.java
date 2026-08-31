package com.titanium.underwriting.web.assembler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.common.enums.VehicleUsageType;
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
}
