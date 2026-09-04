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
import com.titanium.underwriting.api.response.UnderwritingResponse;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.vo.UnderwritingVO;

/**
 * UnderwritingWebMapper 单元测试
 * <p>
 * 覆盖读模型结果→VO、Response→VO 映射与同步决策事件→Response 的声明式映射。
 * 边界输入→命令的装配逻辑已剥离至 {@code UnderwritingWebAssembler}（见 UnderwritingWebAssemblerTest）。
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
    @DisplayName("Response → VO：同名字段映射，空入参返回 null")
    void testDtoToVO() {
        UnderwritingResponse dto = new UnderwritingResponse();
        dto.setUnderwritingId("UW202401001");
        dto.setPolicyId("POL202401001");

        UnderwritingVO vo = mapper.toVO(dto);

        assertNotNull(vo);
        assertEquals("UW202401001", vo.getUnderwritingId());
        assertNull(mapper.toVO((UnderwritingResponse) null));
    }

    @Test
    @DisplayName("同步决策响应：标准体结论直接返回 ACCEPT")
    void shouldMapStandardDecisionToSynchronousResponse() {
        UnderwritingDecidedEvent event = new UnderwritingDecidedEvent(new UnderwritingId("UW-001"),
                PolicyId.of("POL-001"), UnderwritingEnum.RiskLevel.STANDARD,
                UnderwritingEnum.ConclusionType.ACCEPT, UnderwritingEnum.AuditType.AUTOMATIC,
                UnderwritingEnum.UnderwritingStatus.PENDING, UnderwritingEnum.UnderwritingStatus.STANDARD,
                0, null, LocalDateTime.now(), "system", "TENANT-001", null);

        UnderwritingResponse response = mapper.toResponse(event);

        assertEquals(UnderwritingEnum.RiskLevel.STANDARD, response.getRiskLevel());
        assertEquals(UnderwritingEnum.ConclusionType.ACCEPT, response.getConclusionType());
        assertEquals(UnderwritingEnum.UnderwritingStatus.STANDARD, response.getStatus());
    }
}
