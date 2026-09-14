package com.titanium.underwriting.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

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
import org.springframework.http.ResponseEntity;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.underwriting.UnderwriteRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
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

        assertEquals(HttpStatus.CREATED,
                provider.createUnderwriting(new CreateUnderwritingRequest(), TENANT_ID).getStatusCode());
        assertEquals(HttpStatus.OK, provider.underwrite("UW-001", new UnderwriteRequest(), TENANT_ID).getStatusCode());
        assertEquals(HttpStatus.OK,
                provider.submitInput("UW-001", new SubmitUnderwritingInputApiRequest(), TENANT_ID).getStatusCode());
        assertEquals(HttpStatus.OK,
                provider.decide("UW-001", new DecideUnderwritingApiRequest(), TENANT_ID).getStatusCode());

        verifyNoInteractions(queryService);
    }

    @Test
    void statusQueryReturnsMappedPageContentSoEmptyStubIsGone() {
        UnderwritingQueryResult result = mock(UnderwritingQueryResult.class);
        UnderwritingResponse response = mock(UnderwritingResponse.class);
        when(queryService.findUnderwritingsByStatus(eq(UnderwritingEnum.UnderwritingStatus.PENDING),
                any(Pageable.class), eq(TENANT_ID))).thenReturn(new PageImpl<>(List.of(result)));
        when(mapper.toResponse(result)).thenReturn(response);

        ResponseEntity<List<UnderwritingResponse>> entity = provider.getUnderwritingsByStatus("PENDING", 0, 20,
                TENANT_ID);

        assertEquals(List.of(response), entity.getBody(), "空桩已废：必须回读模型当页内容并按契约映射为 Response");
    }

    @Test
    void allQueryUsesEmptyConditionSpecificationSoNoDedicatedFindAllIsNeeded() {
        when(queryService.findUnderwritingsByMultipleConditions(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class), eq(TENANT_ID))).thenReturn(Page.empty());

        ResponseEntity<List<UnderwritingResponse>> entity = provider.getAllUnderwritings(0, 20, TENANT_ID);

        assertEquals(List.of(), entity.getBody());
    }

    @Test
    void oversizedPagingParamsAreNormalizedSoContractCannotForceWholeTableScan() {
        when(queryService.findUnderwritingsByStatus(any(UnderwritingEnum.UnderwritingStatus.class),
                any(Pageable.class), any(String.class))).thenReturn(Page.empty());

        provider.getUnderwritingsByStatus("PENDING", -1, 100000, TENANT_ID);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).findUnderwritingsByStatus(any(UnderwritingEnum.UnderwritingStatus.class), captor.capture(),
                any(String.class));
        assertEquals(0, captor.getValue().getPageNumber(), "页码下限为 0");
        assertEquals(200, captor.getValue().getPageSize(), "单页条数上限 200，防止远程契约拖垮读库");
    }
}
