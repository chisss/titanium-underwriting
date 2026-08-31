package com.titanium.underwriting.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.titanium.underwriting.api.response.UnderwritingResponse;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.assembler.UnderwritingWebAssembler;
import com.titanium.underwriting.web.dto.CreateUnderwritingDTO;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;
import com.titanium.underwriting.web.vo.UnderwritingVO;

/**
 * UnderwritingController 单元测试
 * <p>
 * 整改后：Controller 经 WebMapper 把 web Request 转领域命令交命令门面，写请求直接使用同步命令结果；
 * 仅查询请求访问异步读模型，不再依赖 UnderwritingApi 自调用。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UnderwritingControllerTest {

    @Mock
    private UnderwritingCommandService  underwritingCommandService;

    @Mock
    private UnderwritingQueryAppService underwritingQueryAppService;

    @Mock
    private UnderwritingWebMapper       underwritingWebMapper;

    @Mock
    private UnderwritingWebAssembler    underwritingWebAssembler;

    @InjectMocks
    private UnderwritingController      underwritingController;

    private UnderwritingQueryResult     mockResult;
    private UnderwritingVO              mockVO;
    private CreateUnderwritingDTO       mockRequest;

    @BeforeEach
    void setUp() {
        mockResult = new UnderwritingQueryResult();
        mockResult.setUnderwritingId("UW202401001");
        mockResult.setPolicyId("POL202401001");

        mockVO = new UnderwritingVO();
        mockVO.setUnderwritingId("UW202401001");
        mockVO.setPolicyId("POL202401001");

        mockRequest = new CreateUnderwritingDTO();
    }

    @Test
    void testCreateUnderwriting() {
        // Given
        CreateUnderwritingCommand command = new CreateUnderwritingCommand(new UnderwritingId("UW202401001"), null, null,
                null, null, null, "tenant123", null);
        UnderwritingResponse commandResponse = new UnderwritingResponse();
        commandResponse.setUnderwritingId("UW202401001");
        commandResponse.setPolicyId("POL202401001");
        when(underwritingWebAssembler.toCommand(any(CreateUnderwritingDTO.class), anyString())).thenReturn(command);
        when(underwritingCommandService.createUnderwriting(command)).thenReturn("UW202401001");
        when(underwritingWebMapper.toResponse(command)).thenReturn(commandResponse);
        when(underwritingWebMapper.toVO(commandResponse)).thenReturn(mockVO);

        // When
        ResponseEntity<UnderwritingVO> response = underwritingController.createUnderwriting(mockRequest, "tenant123");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UW202401001", response.getBody().getUnderwritingId());
        assertEquals("POL202401001", response.getBody().getPolicyId());
        verifyNoInteractions(underwritingQueryAppService);
    }

    @Test
    void testGetUnderwritingById() {
        // Given
        when(underwritingQueryAppService.findUnderwritingById(any(UnderwritingId.class), anyString()))
                .thenReturn(mockResult);
        when(underwritingWebMapper.toVO(mockResult)).thenReturn(mockVO);

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
