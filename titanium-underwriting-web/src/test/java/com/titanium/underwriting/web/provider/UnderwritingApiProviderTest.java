package com.titanium.underwriting.web.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;
import com.titanium.underwriting.application.query.UnderwritingQueryAppService;
import com.titanium.underwriting.application.service.UnderwritingCommandService;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.web.assembler.UnderwritingWebAssembler;
import com.titanium.underwriting.web.mapper.UnderwritingWebMapper;

@ExtendWith(MockitoExtension.class)
class UnderwritingApiProviderTest {

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
        CreateUnderwritingCommand createCommand = org.mockito.Mockito.mock(CreateUnderwritingCommand.class);
        when(assembler.toCommand(any(CreateUnderwritingRequest.class), any(String.class))).thenReturn(createCommand);
        when(commandService.createUnderwriting(createCommand)).thenReturn("UW-001");

        UnderwriteCommand underwriteCommand = org.mockito.Mockito.mock(UnderwriteCommand.class);
        when(assembler.toCommand(any(String.class), any(UnderwriteRequest.class), any(String.class)))
                .thenReturn(underwriteCommand);
        SubmitUnderwritingInputCommand submitCommand = org.mockito.Mockito.mock(SubmitUnderwritingInputCommand.class);
        when(assembler.toCommand(any(String.class), any(SubmitUnderwritingInputApiRequest.class), any(String.class)))
                .thenReturn(submitCommand);
        DecideUnderwritingCommand decideCommand = org.mockito.Mockito.mock(DecideUnderwritingCommand.class);
        when(assembler.toCommand(any(String.class), any(DecideUnderwritingApiRequest.class), any(String.class)))
                .thenReturn(decideCommand);

        assertEquals(HttpStatus.CREATED,
                provider.createUnderwriting(new CreateUnderwritingRequest(), "TENANT-001").getStatusCode());
        assertEquals(HttpStatus.OK,
                provider.underwrite("UW-001", new UnderwriteRequest(), "TENANT-001").getStatusCode());
        assertEquals(HttpStatus.OK,
                provider.submitInput("UW-001", new SubmitUnderwritingInputApiRequest(), "TENANT-001").getStatusCode());
        assertEquals(HttpStatus.OK,
                provider.decide("UW-001", new DecideUnderwritingApiRequest(), "TENANT-001").getStatusCode());

        verifyNoInteractions(queryService);
    }
}
