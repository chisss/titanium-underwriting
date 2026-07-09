package com.titanium.underwriting.web.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.common.enums.VehicleUsageType;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.web.request.SubmitUnderwritingInputRequest;
import com.titanium.underwriting.web.vo.UnderwritingVO;

/**
 * UnderwritingWebMapper 单元测试
 * <p>
 * 覆盖读模型结果→VO、DTO→VO 映射，以及险种输入分块装配与核保方式 code 解析（整改后由 WebMapper 内聚）。
 * </p>
 */
class UnderwritingWebMapperTest {

    private final UnderwritingWebMapper mapper = Mappers.getMapper(UnderwritingWebMapper.class);

    @Test
    @DisplayName("读模型结果 → VO：同名字段映射")
    void testResultToVO() {
        UnderwritingQueryResult result = new UnderwritingQueryResult();
        result.setUnderwritingId("UW202401001");
        result.setPolicyId("POL202401001");
        result.setCustomerId("CUST202401001");
        result.setAmount(new BigDecimal("500000.00"));
        result.setUnderwritingType(UnderwritingEnum.UnderwritingType.NEW_BUSINESS);
        result.setCreatedAt(LocalDateTime.now());
        result.setVersion(1L);

        UnderwritingVO vo = mapper.toVO(result);

        assertNotNull(vo);
        assertEquals(result.getUnderwritingId(), vo.getUnderwritingId());
        assertEquals(result.getPolicyId(), vo.getPolicyId());
        assertEquals(result.getAmount(), vo.getAmount());
        assertEquals(result.getUnderwritingType(), vo.getUnderwritingType());
        assertEquals(result.getVersion(), vo.getVersion());
    }

    @Test
    @DisplayName("DTO → VO：同名字段映射，空入参返回 null")
    void testDtoToVO() {
        UnderwritingDTO dto = new UnderwritingDTO();
        dto.setUnderwritingId("UW202401001");
        dto.setPolicyId("POL202401001");

        UnderwritingVO vo = mapper.toVO(dto);

        assertNotNull(vo);
        assertEquals("UW202401001", vo.getUnderwritingId());
        assertNull(mapper.toVO((UnderwritingDTO) null));
    }

    @Test
    @DisplayName("仅填车辆风险时，其余三块为 null，使用性质 code 转枚举")
    void shouldAssembleOnlyVehicleSection() {
        SubmitUnderwritingInputRequest request = new SubmitUnderwritingInputRequest();
        SubmitUnderwritingInputRequest.VehicleRiskInput vehicle = new SubmitUnderwritingInputRequest.VehicleRiskInput();
        vehicle.setVehicleAgeYears(3);
        vehicle.setUsageNature("FAMILY");
        vehicle.setHistoricalClaimCount(1);
        vehicle.setNcdFactor(new BigDecimal("0.85"));
        request.setVehicleRiskInfo(vehicle);

        UnderwritingInput input = mapper.toInput(request);

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
        SubmitUnderwritingInputRequest request = new SubmitUnderwritingInputRequest();
        SubmitUnderwritingInputRequest.HealthDeclarationInput health =
                new SubmitUnderwritingInputRequest.HealthDeclarationInput();
        health.setHeightCm(new BigDecimal("175"));
        health.setWeightKg(new BigDecimal("70"));
        health.setSmoking(false);
        request.setHealthDeclaration(health);

        UnderwritingInput input = mapper.toInput(request);

        assertNotNull(input.healthDeclaration());
        assertEquals(new BigDecimal("175"), input.healthDeclaration().heightCm());
    }

    @Test
    @DisplayName("核保方式：null/空回落为 AUTOMATIC，有值按 code 解析")
    void shouldResolveAuditType() {
        assertEquals(UnderwritingEnum.AuditType.AUTOMATIC, mapper.toAuditType(null));
        assertEquals(UnderwritingEnum.AuditType.AUTOMATIC, mapper.toAuditType(""));
        assertEquals(UnderwritingEnum.AuditType.MANUAL, mapper.toAuditType("MANUAL"));
        assertEquals(UnderwritingEnum.AuditType.HYBRID, mapper.toAuditType("HYBRID"));
    }
}
