package com.titanium.underwriting.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.underwriting.api.request.underwriting.AutoDecideUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.ManualReviewRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.underwriting.UnderwriteRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.AutoDecideRequest;
import com.titanium.underwriting.valueobject.AutoDecideResult;
import com.titanium.underwriting.web.assembler.UnderwritingWebAssembler;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;

@ExtendWith(MockitoExtension.class)
class UnderwritingApiProviderTest {

    private static final String TENANT_ID = "TENANT-001";

    @Mock
    private UnderwritingCommandService  commandService;

    @Mock
    private UnderwritingQueryAppService queryService;

    @Mock
    private UnderwritingWebMapper       mapper;

    @Mock
    private UnderwritingWebAssembler    assembler;

    @InjectMocks
    private UnderwritingApiProvider     provider;

    @BeforeEach
    void setUp() {
        // 契约实现已改为从请求上下文取租户（不再由入参显式传递），故测试须先建立租户上下文
        RequestContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void writeApisDoNotReadAsynchronousProjection() {
        CreateUnderwritingCommand createCommand = mock(CreateUnderwritingCommand.class);
        when(assembler.toCommand(any(CreateUnderwritingRequest.class), any(String.class))).thenReturn(createCommand);
        when(commandService.createUnderwriting(createCommand)).thenReturn(createCommand);

        UnderwriteCommand underwriteCommand = mock(UnderwriteCommand.class);
        when(assembler.toCommand(any(String.class), any(UnderwriteRequest.class), any(String.class)))
                .thenReturn(underwriteCommand);
        SubmitUnderwritingInputCommand submitCommand = mock(SubmitUnderwritingInputCommand.class);
        when(assembler.toCommand(any(String.class), any(SubmitUnderwritingInputApiRequest.class), any(String.class)))
                .thenReturn(submitCommand);
        DecideUnderwritingCommand decideCommand = mock(DecideUnderwritingCommand.class);
        when(assembler.toCommand(any(String.class), any(DecideUnderwritingApiRequest.class), any(String.class)))
                .thenReturn(decideCommand);
        // g02-03：转人工契约与 web 端点平行收敛，同样不得回读异步投影
        ManualReviewCommand manualReviewCommand = mock(ManualReviewCommand.class);
        when(assembler.toCommand(any(String.class), any(ManualReviewRequest.class), any(String.class)))
                .thenReturn(manualReviewCommand);
        // g02-04：粗粒度自动决策同样以同步回执为准（结论来自命令结果，不回读投影）
        AutoDecideRequest autoDecideRequest = mock(AutoDecideRequest.class);
        when(assembler.toAutoDecideRequest(any(AutoDecideUnderwritingRequest.class), any(String.class)))
                .thenReturn(autoDecideRequest);
        AutoDecideResult autoDecideResult = mock(AutoDecideResult.class);
        when(commandService.autoDecide(autoDecideRequest)).thenReturn(autoDecideResult);

        assertEquals(HttpStatus.CREATED,
                provider.createUnderwriting(new CreateUnderwritingRequest()).getStatusCode());
        // m24-01a：除 createUnderwriting（保留 201 状态语义的 ResponseEntity 外层）外，其余写端点改为裸信封，
        // 「成功」由信封 isSuccess 表达（HTTP 200 由全局序列化给出），不再经 ResponseEntity 判状态码
        assertTrue(provider.underwrite("UW-001", new UnderwriteRequest()).isSuccess());
        assertTrue(provider.submitInput("UW-001", new SubmitUnderwritingInputApiRequest()).isSuccess());
        assertTrue(provider.decide("UW-001", new DecideUnderwritingApiRequest()).isSuccess());
        assertTrue(provider.manualReview("UW-001", new ManualReviewRequest()).isSuccess());
        assertTrue(provider.autoDecide(new AutoDecideUnderwritingRequest()).isSuccess());
        // 同步回执链完整：命令结果经 Mapper 映射为契约 Response，不经读模型
        verify(mapper).toResponse(autoDecideResult);

        verifyNoInteractions(queryService);
    }

    @Test
    void statusQueryReturnsMappedPageContentSoEmptyStubIsGone() {
        UnderwritingQueryResult result = mock(UnderwritingQueryResult.class);
        UnderwritingResponse response = mock(UnderwritingResponse.class);
        when(queryService.findUnderwritingsByStatus(eq(UnderwritingEnum.UnderwritingStatus.PENDING),
                any(Pageable.class), eq(TENANT_ID))).thenReturn(new PageImpl<>(List.of(result)));
        when(mapper.toResponse(result)).thenReturn(response);

        ApiResponse<List<UnderwritingResponse>> envelope = provider.getUnderwritingsByStatus("PENDING", 0, 20);

        assertEquals(List.of(response), envelope.getData(), "空桩已废：必须回读模型当页内容并按契约映射为 Response");
    }

    @Test
    void allQueryUsesEmptyConditionSpecificationSoNoDedicatedFindAllIsNeeded() {
        when(queryService.findUnderwritingsByMultipleConditions(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class), eq(TENANT_ID))).thenReturn(Page.empty());

        ApiResponse<List<UnderwritingResponse>> envelope = provider.getAllUnderwritings(0, 20);

        assertEquals(List.of(), envelope.getData());
    }

    @Test
    void oversizedPagingParamsAreNormalizedSoContractCannotForceWholeTableScan() {
        when(queryService.findUnderwritingsByStatus(any(UnderwritingEnum.UnderwritingStatus.class),
                any(Pageable.class), any(String.class))).thenReturn(Page.empty());

        provider.getUnderwritingsByStatus("PENDING", -1, 100000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).findUnderwritingsByStatus(any(UnderwritingEnum.UnderwritingStatus.class), captor.capture(),
                any(String.class));
        assertEquals(0, captor.getValue().getPageNumber(), "页码下限为 0");
        assertEquals(200, captor.getValue().getPageSize(), "单页条数上限 200，防止远程契约拖垮读库");
    }
}
