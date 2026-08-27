package com.titanium.underwriting.aggregate;

import static org.axonframework.test.matchers.Matchers.matches;
import static org.axonframework.test.matchers.Matchers.messageWithPayload;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;

class UnderwritingSynchronousCommandTest {

    private static final UnderwritingId UNDERWRITING_ID = new UnderwritingId("UW-001");
    private static final String         TENANT_ID       = "TENANT-001";

    private FixtureConfiguration<Underwriting> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Underwriting.class);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void underwriteReturnsAppliedStatusEventSynchronously() {
        UnderwriteCommand command = new UnderwriteCommand(UNDERWRITING_ID,
                UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY), "自动核保", "system", TENANT_ID);

        fixture.given(createdEvent())
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches((UnderwritingStatusChangedEvent event) ->
                        event.underwritingId().equals(UNDERWRITING_ID)
                                && event.newStatus() == UnderwritingEnum.UnderwritingStatus.APPROVED)))
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.Matchers.instanceOf(UnderwritingStatusChangedEvent.class))));
    }

    @Test
    void emptyInputIsAValidStandardRiskContractAndIsReturnedSynchronously() {
        UnderwritingInput emptyInput = UnderwritingInput.builder().build();
        SubmitUnderwritingInputCommand submit = new SubmitUnderwritingInputCommand(UNDERWRITING_ID, emptyInput,
                "system", TENANT_ID);

        fixture.given(createdEvent())
                .when(submit)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches((UnderwritingInputSubmittedEvent event) ->
                        event.underwritingId().equals(UNDERWRITING_ID)
                                && event.underwritingInput().equals(emptyInput))))
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.Matchers.instanceOf(UnderwritingInputSubmittedEvent.class))));
    }

    @Test
    void decideEmptyInputReturnsFinalStandardDecisionSynchronously() {
        UnderwritingInput emptyInput = UnderwritingInput.builder().build();
        DecideUnderwritingCommand decide = new DecideUnderwritingCommand(UNDERWRITING_ID,
                UnderwritingEnum.AuditType.AUTOMATIC, "system", TENANT_ID, true);

        fixture.given(createdEvent(), new UnderwritingInputSubmittedEvent(UNDERWRITING_ID, emptyInput,
                LocalDateTime.now(), "system", TENANT_ID))
                .when(decide)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches((UnderwritingDecidedEvent event) ->
                        event.underwritingId().equals(UNDERWRITING_ID)
                                && event.riskLevel() == UnderwritingEnum.RiskLevel.STANDARD
                                && event.conclusionType() == UnderwritingEnum.ConclusionType.ACCEPT
                                && event.newStatus() == UnderwritingEnum.UnderwritingStatus.STANDARD)))
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.Matchers.instanceOf(UnderwritingDecidedEvent.class))));
    }

    private UnderwritingCreatedEvent createdEvent() {
        return new UnderwritingCreatedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"), CustomerId.of("CUS-001"),
                UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, LocalDateTime.now(), "system", TENANT_ID, "PRD-001");
    }
}
