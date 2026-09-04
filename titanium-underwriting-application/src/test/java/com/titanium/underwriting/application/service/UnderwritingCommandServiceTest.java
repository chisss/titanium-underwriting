package com.titanium.underwriting.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.application.orchestration.UnderwritingDecisionOrchestrator;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

@ExtendWith(MockitoExtension.class)
class UnderwritingCommandServiceTest {

    @Mock
    private CommandGateway                  commandGateway;

    @Mock
    private UnderwritingDecisionOrchestrator underwritingDecisionOrchestrator;

    @InjectMocks
    private UnderwritingCommandService      service;

    @Test
    void createReturnsCommandIdentifierWhenAxonReturnsUnderwritingId() {
        UnderwritingId underwritingId = new UnderwritingId("UW-001");
        CreateUnderwritingCommand command = new CreateUnderwritingCommand(underwritingId, PolicyId.of("POL-001"),
                CustomerId.of("CUS-001"), UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, "system", "TENANT-001", "PRD-001");
        when(commandGateway.sendAndWait(command)).thenReturn(underwritingId);

        assertEquals("UW-001", service.createUnderwriting(command));
    }
}
