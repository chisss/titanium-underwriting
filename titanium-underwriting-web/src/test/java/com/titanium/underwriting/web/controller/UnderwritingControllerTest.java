package com.titanium.underwriting.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;
import com.titanium.underwriting.web.vo.UnderwritingVO;

/**
 * UnderwritingController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class UnderwritingControllerTest {

    @Mock
    private UnderwritingApi           underwritingApi;

    @Mock
    private UnderwritingWebMapper     underwritingWebMapper;

    @InjectMocks
    private UnderwritingController    underwritingController;

    private UnderwritingDTO           mockDTO;
    private UnderwritingVO            mockVO;
    private CreateUnderwritingRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockDTO = new UnderwritingDTO();
        mockDTO.setUnderwritingId("UW202401001");
        mockDTO.setPolicyId("POL202401001");

        mockVO = new UnderwritingVO();
        mockVO.setUnderwritingId("UW202401001");
        mockVO.setPolicyId("POL202401001");

        mockRequest = new CreateUnderwritingRequest();
    }

    @Test
    void testCreateUnderwriting() {
        // Given
        when(underwritingApi.createUnderwriting(any(CreateUnderwritingRequest.class), anyString())).thenReturn(ResponseEntity.of(Optional.of(mockDTO)));
        when(underwritingWebMapper.toVO(mockDTO)).thenReturn(mockVO);

        // When
        ResponseEntity<UnderwritingVO> response = underwritingController.createUnderwriting(mockRequest, "tenant123");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UW202401001", response.getBody().getUnderwritingId());
        assertEquals("POL202401001", response.getBody().getPolicyId());
    }

    @Test
    void testGetUnderwritingById() {
        // Given
        when(underwritingApi.getUnderwritingById(anyString(), anyString())).thenReturn(ResponseEntity.of(Optional.of(mockDTO)));
        when(underwritingWebMapper.toVO(mockDTO)).thenReturn(mockVO);

        // When
        ResponseEntity<UnderwritingVO> response = underwritingController.getUnderwritingById("UW202401001",
                "tenant123");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UW202401001", response.getBody().getUnderwritingId());
    }
}
