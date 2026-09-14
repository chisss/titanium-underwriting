package com.titanium.underwriting.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.command.SubmitUnderwritingInputCommand;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.exception.UnderwritingStatusException;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.RuleUnderwritingDecision;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;

/**
 * 核保聚合终态保护测试（m7-1002）。
 * <p>
 * 聚合原有四个命令处理器（自动核保/提交输入/出具结论/转人工）均无状态前置校验，已出结论的核保单可被再次
 * 改写。冲突结论经 {@code underwriting-decided} 按 {@code policyId} 保序投递到 policy 域后，会直接覆盖
 * 前一次承保判断。本测试锁定「终态不可逆」这条红线，并同时校验非终态之间的正常往返不被误伤。
 * </p>
 */
class UnderwritingTerminalStateGuardTest {

    private static final UnderwritingId UNDERWRITING_ID = new UnderwritingId("UW-001");
    private static final String         TENANT_ID       = "TENANT-001";
    private static final String         OPERATOR        = "uw01";
    private static final String         DECLINE_REASON  = "不可保体";

    private FixtureConfiguration<Underwriting> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Underwriting.class);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void decideOnTerminalContractIsRejectedSoDownstreamNeverSeesConflictingConclusions() {
        fixture.given(createdEvent(), inputSubmittedEvent(), standardDecision())
                .when(decideCommand())
                .expectException(UnderwritingStatusException.class);
    }

    @Test
    void underwriteOnTerminalContractIsRejectedSoConcludedCasesAreNotReAssessed() {
        fixture.given(createdEvent(), inputSubmittedEvent(), declinedDecision())
                .when(underwriteCommand())
                .expectException(UnderwritingStatusException.class);
    }

    @Test
    void manualReviewOnTerminalContractIsRejectedSoConcludedCasesStayClosed() {
        fixture.given(createdEvent(), inputSubmittedEvent(), declinedDecision())
                .when(manualReviewCommand())
                .expectException(UnderwritingStatusException.class);
    }

    @Test
    void submitInputOnTerminalContractIsRejectedSoReadModelInputStaysConsistentWithConclusion() {
        fixture.given(createdEvent(), inputSubmittedEvent(), standardDecision())
                .when(submitInputCommand())
                .expectException(UnderwritingStatusException.class);
    }

    @Test
    void decideAfterManualReviewStillSucceedsSoManualRoundIsNotBlocked() {
        fixture.given(createdEvent(), inputSubmittedEvent(),
                statusChanged(UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW))
                .when(decideCommand())
                .expectSuccessfulHandlerExecution();
    }

    @Test
    void underwriteAfterReviewStillSucceedsSoAutomaticRetryPathIsNotBlocked() {
        fixture.given(createdEvent(), inputSubmittedEvent(),
                statusChanged(UnderwritingEnum.UnderwritingStatus.REVIEW))
                .when(underwriteCommand())
                .expectSuccessfulHandlerExecution();
    }

    @Test
    void submitInputAfterAwaitingInfoStillSucceedsSoSupplementPathIsNotBlocked() {
        fixture.given(createdEvent(), statusChanged(UnderwritingEnum.UnderwritingStatus.AWAITING_INFO))
                .when(submitInputCommand())
                .expectSuccessfulHandlerExecution();
    }

    @Test
    void declinedDecisionKeepsRejectReasonSoWriteSideAgreesWithReadSide() {
        RuleUnderwritingDecision rejectDecision = new RuleUnderwritingDecision(
                UnderwritingEnum.ConclusionType.REJECT, UnderwritingEnum.RiskLevel.UNINSURABLE,
                UnderwritingEnum.UnderwritingStatus.DECLINED, null, DECLINE_REASON);

        fixture.given(createdEvent(), inputSubmittedEvent())
                .when(new DecideUnderwritingCommand(UNDERWRITING_ID, UnderwritingEnum.AuditType.MANUAL, OPERATOR,
                        TENANT_ID, true, rejectDecision))
                .expectState(state -> assertEquals(DECLINE_REASON, state.getRejectReason(),
                        "拒保口径统一走 isRejected() 后，真实拒保状态 DECLINED 的原因必须落聚合，与读模型一致"));
    }

    private UnderwritingCreatedEvent createdEvent() {
        return new UnderwritingCreatedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"), CustomerId.of("CUS-001"),
                UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, LocalDateTime.now(), OPERATOR, TENANT_ID, "PRD-001",
                "UW202401001");
    }

    private UnderwritingInputSubmittedEvent inputSubmittedEvent() {
        return new UnderwritingInputSubmittedEvent(UNDERWRITING_ID, UnderwritingInput.builder().build(),
                LocalDateTime.now(), OPERATOR, TENANT_ID);
    }

    private UnderwritingStatusChangedEvent statusChanged(UnderwritingEnum.UnderwritingStatus newStatus) {
        return new UnderwritingStatusChangedEvent(UNDERWRITING_ID, UnderwritingEnum.UnderwritingStatus.PENDING,
                newStatus, "流程流转", LocalDateTime.now(), OPERATOR, TENANT_ID);
    }

    private UnderwritingDecidedEvent standardDecision() {
        return decidedEvent(UnderwritingEnum.RiskLevel.STANDARD, UnderwritingEnum.ConclusionType.ACCEPT,
                UnderwritingEnum.UnderwritingStatus.STANDARD, null);
    }

    private UnderwritingDecidedEvent declinedDecision() {
        return decidedEvent(UnderwritingEnum.RiskLevel.UNINSURABLE, UnderwritingEnum.ConclusionType.REJECT,
                UnderwritingEnum.UnderwritingStatus.DECLINED, DECLINE_REASON);
    }

    private UnderwritingDecidedEvent decidedEvent(UnderwritingEnum.RiskLevel riskLevel,
            UnderwritingEnum.ConclusionType conclusion, UnderwritingEnum.UnderwritingStatus newStatus, String reason) {
        return new UnderwritingDecidedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"), riskLevel, conclusion,
                UnderwritingEnum.AuditType.AUTOMATIC, UnderwritingEnum.UnderwritingStatus.PENDING, newStatus, 55,
                null, LocalDateTime.now(), OPERATOR, TENANT_ID, reason);
    }

    private UnderwriteCommand underwriteCommand() {
        return new UnderwriteCommand(UNDERWRITING_ID, UnderwritingAmount.of(BigDecimal.TEN, CurrencyEnum.CNY),
                "重新自动核保", OPERATOR, TENANT_ID);
    }

    private DecideUnderwritingCommand decideCommand() {
        return new DecideUnderwritingCommand(UNDERWRITING_ID, UnderwritingEnum.AuditType.MANUAL, OPERATOR, TENANT_ID,
                true, null);
    }

    private ManualReviewCommand manualReviewCommand() {
        return new ManualReviewCommand(UNDERWRITING_ID, "风险偏高转人工", OPERATOR, TENANT_ID);
    }

    private SubmitUnderwritingInputCommand submitInputCommand() {
        return new SubmitUnderwritingInputCommand(UNDERWRITING_ID, UnderwritingInput.builder().build(), OPERATOR,
                TENANT_ID);
    }
}
