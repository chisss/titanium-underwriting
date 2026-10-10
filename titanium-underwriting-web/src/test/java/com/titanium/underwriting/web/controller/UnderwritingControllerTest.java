package com.titanium.underwriting.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.titanium.common.context.RequestContext;
import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.web.assembler.UnderwritingWebAssembler;
import com.titanium.underwriting.web.dto.CreateUnderwritingDTO;
import com.titanium.underwriting.web.dto.ManualReviewDTO;
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
        // m18：租户改由请求上下文承载，控制器不再以 @RequestHeader 形参接收；
        // 无 Spring 上下文时 RequestContextHolder 是纯 ThreadLocal，直接置入即可
        RequestContextHolder.set(RequestContext.ofTenant("tenant123"));

        mockResult = new UnderwritingQueryResult();
        mockResult.setUnderwritingId("UW202401001");
        mockResult.setPolicyId("POL202401001");

        mockVO = new UnderwritingVO();
        mockVO.setUnderwritingId("UW202401001");
        mockVO.setPolicyId("POL202401001");

        mockRequest = new CreateUnderwritingDTO();
    }

    @AfterEach
    void tearDown() {
        // 线程池复用线程上残留身份会造成跨用例串台
        RequestContextHolder.clear();
    }

    @Test
    void testCreateUnderwriting() {
        // Given
        CreateUnderwritingCommand command = new CreateUnderwritingCommand(new UnderwritingId("UW202401001"), null, null,
                null, null, null, "tenant123", null, null, null);
        UnderwritingResponse commandResponse = new UnderwritingResponse();
        commandResponse.setUnderwritingId("UW202401001");
        commandResponse.setPolicyId("POL202401001");
        when(underwritingWebAssembler.toCommand(any(CreateUnderwritingDTO.class), anyString())).thenReturn(command);
        when(underwritingCommandService.createUnderwriting(command)).thenReturn(command);
        when(underwritingWebMapper.toResponse(command)).thenReturn(commandResponse);
        when(underwritingWebMapper.toVO(commandResponse)).thenReturn(mockVO);

        // When
        // m24-01b：web 端点统一返回 ApiResponse 信封，载荷下移至 data
        ResponseEntity<ApiResponse<UnderwritingVO>> response = underwritingController.createUnderwriting(mockRequest);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApiResponse.SUCCESS_CODE, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
        assertEquals("UW202401001", response.getBody().getData().getUnderwritingId());
        assertEquals("POL202401001", response.getBody().getData().getPolicyId());
        verifyNoInteractions(underwritingQueryAppService);
    }

    @Test
    void testGetUnderwritingById() {
        // Given
        when(underwritingQueryAppService.findUnderwritingById(any(UnderwritingId.class), anyString()))
                .thenReturn(mockResult);
        when(underwritingWebMapper.toVO(mockResult)).thenReturn(mockVO);

        // When
        ApiResponse<UnderwritingVO> response = underwritingController.getUnderwritingById("UW202401001");

        // Then
        assertNotNull(response);
        assertEquals(ApiResponse.SUCCESS_CODE, response.getCode());
        assertNotNull(response.getData());
        assertEquals("UW202401001", response.getData().getUnderwritingId());
    }

    @Test
    void testSearchUnderwritingsWithUnderwritingType() {
        // Given
        Page<UnderwritingQueryResult> mockPage = new PageImpl<>(List.of(mockResult));
        when(underwritingQueryAppService.findUnderwritingsByMultipleConditions(
                isNull(), eq(UnderwritingEnum.UnderwritingType.NEW_BUSINESS), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(PageRequest.class), anyString())).thenReturn(mockPage);
        when(underwritingWebMapper.toVO(mockResult)).thenReturn(mockVO);

        // When
        ApiResponse<Page<UnderwritingVO>> response = underwritingController.searchUnderwritings(
                null, "NEW_BUSINESS", null, null, null, 0, 10);

        // Then
        assertNotNull(response);
        assertEquals(ApiResponse.SUCCESS_CODE, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getTotalElements());
        assertEquals("UW202401001", response.getData().getContent().get(0).getUnderwritingId());
    }

    @Test
    void testSearchUnderwritingsWithIllegalEnumCodeIgnored() {
        // Given：非法 code 经 fromCode 解析为 null，按不传条件处理，不抛异常
        Page<UnderwritingQueryResult> mockPage = new PageImpl<>(List.of(mockResult));
        when(underwritingQueryAppService.findUnderwritingsByMultipleConditions(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(PageRequest.class), anyString())).thenReturn(mockPage);
        when(underwritingWebMapper.toVO(mockResult)).thenReturn(mockVO);

        // When
        ApiResponse<Page<UnderwritingVO>> response = underwritingController.searchUnderwritings(
                "UNKNOWN_STATUS", "UNKNOWN_TYPE", null, null, null, 0, 10);

        // Then
        assertNotNull(response);
        assertEquals(ApiResponse.SUCCESS_CODE, response.getCode());
        assertNotNull(response.getData());
    }

    @Test
    void testManualReviewReturnsStatusChangedEventWithoutReadingProjection() {
        // Given：转人工请求经装配器转命令，门面返回聚合冻结的状态变更事件（g02-03）
        ManualReviewDTO request = new ManualReviewDTO();
        request.setReviewComments("保额超阈值，需人工复核");
        request.setReviewedBy("uw01");
        ManualReviewCommand command = new ManualReviewCommand(new UnderwritingId("UW202401001"),
                "保额超阈值，需人工复核", "uw01", "tenant123");
        UnderwritingStatusChangedEvent event = new UnderwritingStatusChangedEvent(new UnderwritingId("UW202401001"),
                UnderwritingEnum.UnderwritingStatus.REVIEW, UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW,
                "保额超阈值，需人工复核", LocalDateTime.now(), "uw01", "tenant123");
        UnderwritingResponse eventResponse = new UnderwritingResponse();
        eventResponse.setUnderwritingId("UW202401001");

        when(underwritingWebAssembler.toCommand(eq("UW202401001"), any(ManualReviewDTO.class), eq("tenant123")))
                .thenReturn(command);
        when(underwritingCommandService.manualReview(command)).thenReturn(event);
        when(underwritingWebMapper.toResponse(event)).thenReturn(eventResponse);
        when(underwritingWebMapper.toVO(eventResponse)).thenReturn(mockVO);

        // When
        ApiResponse<UnderwritingVO> response = underwritingController.manualReview("UW202401001", request);

        // Then：写回执走同步命令结果，不触碰异步读模型
        assertEquals(ApiResponse.SUCCESS_CODE, response.getCode());
        assertNotNull(response.getData());
        assertEquals("UW202401001", response.getData().getUnderwritingId());
        verifyNoInteractions(underwritingQueryAppService);
    }
}
