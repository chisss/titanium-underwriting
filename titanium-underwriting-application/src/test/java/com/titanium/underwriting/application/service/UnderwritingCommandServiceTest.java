package com.titanium.underwriting.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.titanium.underwriting.application.orchestration.UnderwritingRoutingOrchestrator;
import com.titanium.underwriting.command.CreateUnderwritingCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.generator.UnderwritingNoGenerator;
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

    @Mock
    private UnderwritingRoutingOrchestrator  underwritingRoutingOrchestrator;

    @Mock
    private UnderwritingNoGenerator         underwritingNoGenerator;

    @InjectMocks
    private UnderwritingCommandService      service;

    @Test
    void underwriteDelegatesToRoutingOrchestratorSoThresholdComesFromProductConfig() {
        UnderwriteCommand command = new UnderwriteCommand(new UnderwritingId("UW-001"),
                UnderwritingAmount.of(BigDecimal.valueOf(600_000), CurrencyEnum.CNY), "自动核保", "system",
                "TENANT-001", null);
        UnderwritingStatusChangedEvent event = new UnderwritingStatusChangedEvent(command.underwritingId(), null,
                UnderwritingEnum.UnderwritingStatus.REVIEW, "自动核保", null, "system", "TENANT-001");
        when(underwritingRoutingOrchestrator.route(command)).thenReturn(event);

        // 门面不得直接 commandGateway.sendAndWait（那会绕过产品阈值充实），必须经金额路由编排器
        assertSame(event, service.underwrite(command));
        verify(underwritingRoutingOrchestrator).route(command);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void createReturnsNumberedCommandWhenCaseNoAbsent() {
        UnderwritingId underwritingId = new UnderwritingId("UW-001");
        CreateUnderwritingCommand command = new CreateUnderwritingCommand(underwritingId, PolicyId.of("POL-001"),
                CustomerId.of("CUS-001"), UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, "system", "TENANT-001", "PRD-001", null, null);
        when(underwritingNoGenerator.generateUnderwritingNo("TENANT-001")).thenReturn("UW202609090000001");
        when(commandGateway.sendAndWait(any(CreateUnderwritingCommand.class))).thenReturn(underwritingId);

        // 案号缺省时由发号端口回填，返回编号后的命令供调用方回显
        CreateUnderwritingCommand numbered = service.createUnderwriting(command);

        assertEquals("UW-001", numbered.underwritingId().value());
        assertNotNull(numbered.caseNo());
        assertEquals("UW202609090000001", numbered.caseNo());
    }

    @Test
    void createKeepsUpstreamCaseNoWhenPresent() {
        UnderwritingId underwritingId = new UnderwritingId("UW-001");
        CreateUnderwritingCommand command = new CreateUnderwritingCommand(underwritingId, PolicyId.of("POL-001"),
                CustomerId.of("CUS-001"), UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, "system", "TENANT-001", "PRD-001",
                "UW202609090000002", null);
        when(commandGateway.sendAndWait(command)).thenReturn(underwritingId);

        // 上游显式携带案号时不再取号（幂等重放），原命令原样返回
        CreateUnderwritingCommand numbered = service.createUnderwriting(command);

        assertEquals("UW202609090000002", numbered.caseNo());
    }
}
