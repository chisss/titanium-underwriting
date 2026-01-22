package com.titanium.underwriting.web.mapper;

import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.web.vo.UnderwritingVO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UnderwritingWebMapper 单元测试
 */
class UnderwritingWebMapperTest {

    private final UnderwritingWebMapper mapper = Mappers.getMapper(UnderwritingWebMapper.class);

    @Test
    void testToVO() {
        // Given
        UnderwritingDTO dto = new UnderwritingDTO();
        dto.setUnderwritingId("UW202401001");
        dto.setPolicyId("POL202401001");
        dto.setCustomerId("CUST202401001");
        dto.setAmount(new BigDecimal("500000.00"));
        dto.setUnderwritingType("寿险核保");
        dto.setCreatedAt(LocalDateTime.now());
        dto.setVersion(1L);

        // When
        UnderwritingVO vo = mapper.toVO(dto);

        // Then
        assertNotNull(vo);
        assertEquals(dto.getUnderwritingId(), vo.getUnderwritingId());
        assertEquals(dto.getPolicyId(), vo.getPolicyId());
        assertEquals(dto.getCustomerId(), vo.getCustomerId());
        assertEquals(dto.getAmount(), vo.getAmount());
        assertEquals(dto.getUnderwritingType(), vo.getUnderwritingType());
        assertEquals(dto.getCreatedAt(), vo.getCreatedAt());
        assertEquals(dto.getVersion(), vo.getVersion());
    }

    @Test
    void testToVOWithNullInput() {
        // When
        UnderwritingVO vo = mapper.toVO(null);

        // Then
        assertNull(vo);
    }

    @Test
    void testToVOList() {
        // Given
        UnderwritingDTO dto1 = new UnderwritingDTO();
        dto1.setUnderwritingId("UW202401001");
        dto1.setPolicyId("POL202401001");

        UnderwritingDTO dto2 = new UnderwritingDTO();
        dto2.setUnderwritingId("UW202401002");
        dto2.setPolicyId("POL202401002");

        List<UnderwritingDTO> dtoList = Arrays.asList(dto1, dto2);

        // When
        List<UnderwritingVO> voList = mapper.toVOList(dtoList);

        // Then
        assertNotNull(voList);
        assertEquals(2, voList.size());
        assertEquals("UW202401001", voList.get(0).getUnderwritingId());
        assertEquals("UW202401002", voList.get(1).getUnderwritingId());
    }

    @Test
    void testToVOListWithNullInput() {
        // When
        List<UnderwritingVO> voList = mapper.toVOList(null);

        // Then
        assertNull(voList);
    }
}