package com.titanium.underwriting.aggregate;

import static org.axonframework.test.matchers.Matchers.matches;
import static org.axonframework.test.matchers.Matchers.messageWithPayload;

import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.underwriting.command.AssessMaintenanceUnderwritingCommand;
import com.titanium.underwriting.event.MaintenanceUnderwritingAssessedEvent;
import com.titanium.underwriting.exception.UnderwritingValidationException;
import com.titanium.underwriting.valueobject.MaintenanceRiskFieldChange;
import com.titanium.underwriting.valueobject.MaintenanceUnderwritingConclusion;
import com.titanium.underwriting.valueobject.UnderwritingId;

class MaintenanceUnderwritingAggregateTest {

    private FixtureConfiguration<Underwriting> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Underwriting.class);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void shouldApproveRecognizedAutomaticRiskChanges() {
        AssessMaintenanceUnderwritingCommand command = command("UW_AUTO_ACCEPT_ADDRESS");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches(
                        (MaintenanceUnderwritingAssessedEvent event) ->
                                event.conclusion() == MaintenanceUnderwritingConclusion.APPROVED
                                        && event.completedAt() != null)))
                .expectEventsMatching(org.axonframework.test.matchers.Matchers.payloadsMatching(
                        org.axonframework.test.matchers.Matchers.exactSequenceOf(
                                org.hamcrest.Matchers.instanceOf(MaintenanceUnderwritingAssessedEvent.class))));
    }

    @Test
    void shouldSupportRejectConditionalAndManualOutcomes() {
        assertConclusion("UW_REJECT_HEALTH", MaintenanceUnderwritingConclusion.REJECTED);
        assertConclusion("UW_CONDITIONAL_OCCUPATION", MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED);
        assertConclusion("UNKNOWN_CHANGE", MaintenanceUnderwritingConclusion.MANUAL_REVIEW);
    }

    @Test
    void shouldConditionallyApproveFormalCoverageAmountChange() {
        fixture.givenNoPriorActivity()
                .when(command("COVERAGE_AMOUNT_CHANGE"))
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches(
                        (MaintenanceUnderwritingAssessedEvent event) ->
                                event.conclusion() == MaintenanceUnderwritingConclusion.CONDITIONAL_APPROVED
                                        && event.additionalConditions().equals(
                                                List.of("REVIEW_FIELD:insured.risk"))
                                        && event.completedAt() != null)));
    }

    @Test
    void shouldReturnNotRequiredOnlyWhenConfigurationAndRiskBothAllowIt() {
        AssessMaintenanceUnderwritingCommand command = command(false, List.of());

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches(
                        (MaintenanceUnderwritingAssessedEvent event) ->
                                event.conclusion() == MaintenanceUnderwritingConclusion.NOT_REQUIRED)));
    }

    @Test
    void shouldRejectDifferentPayloadForSameIdempotencyKey() {
        AssessMaintenanceUnderwritingCommand first = command("UW_AUTO_ACCEPT_ADDRESS");
        MaintenanceUnderwritingAssessedEvent prior = event(first, MaintenanceUnderwritingConclusion.APPROVED);
        AssessMaintenanceUnderwritingCommand changed = command("UW_REJECT_HEALTH");

        fixture.given(prior)
                .when(changed)
                .expectException(UnderwritingValidationException.class)
                .expectNoEvents();
    }

    private void assertConclusion(String changeTypeCode, MaintenanceUnderwritingConclusion expected) {
        FixtureConfiguration<Underwriting> scenario = new AggregateTestFixture<>(Underwriting.class);
        scenario.setReportIllegalStateChange(false);
        scenario.givenNoPriorActivity()
                .when(command(changeTypeCode))
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches(
                        (MaintenanceUnderwritingAssessedEvent event) -> event.conclusion() == expected)));
    }

    private AssessMaintenanceUnderwritingCommand command(String changeTypeCode) {
        return command(true, List.of(new MaintenanceRiskFieldChange(
                "insured-1", "insured.risk", "TEXT", "LOW", "HIGH", changeTypeCode)));
    }

    private AssessMaintenanceUnderwritingCommand command(
            boolean required,
            List<MaintenanceRiskFieldChange> changes) {
        UnderwritingId id = UnderwritingId.forMaintenance("tenant-1", "case-1:task-1");
        AssessMaintenanceUnderwritingCommand draft = new AssessMaintenanceUnderwritingCommand(
                id, "tenant-1", "case-1", "policy-1", 7L, "product-1", "product-v3", "plan-v2",
                "POLICY_INFO_CHANGE", "config-v4", "a".repeat(64), required, changes,
                "case-1:task-1", "0".repeat(64), "maintenance-service");
        return new AssessMaintenanceUnderwritingCommand(
                id, draft.tenantId(), draft.maintenanceId(), draft.policyId(), draft.policyBaselineVersion(),
                draft.productId(), draft.productVersion(), draft.planVersion(), draft.itemCode(),
                draft.configurationVersion(), draft.configurationContentHash(), required, changes,
                draft.idempotencyKey(), draft.calculatePayloadHash(), draft.requestedBy());
    }

    private MaintenanceUnderwritingAssessedEvent event(
            AssessMaintenanceUnderwritingCommand command,
            MaintenanceUnderwritingConclusion conclusion) {
        return new MaintenanceUnderwritingAssessedEvent(
                command.underwritingId(), command.tenantId(), command.maintenanceId(), command.policyId(),
                command.policyBaselineVersion(), command.itemCode(), command.idempotencyKey(), command.payloadHash(),
                "maintenance-underwriting-rules/1.0.0", "deterministic-change-classifier/1.0.0",
                conclusion, List.of(), "版本化规则判定风险可接受",
                java.time.LocalDateTime.parse("2026-08-25T12:00:00"),
                java.time.LocalDateTime.parse("2026-08-25T12:00:00"), "maintenance-service");
    }
}
