package com.titanium.underwriting.aggregate;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.matches;
import static org.axonframework.test.matchers.Matchers.messageWithPayload;
import static org.axonframework.test.matchers.Matchers.payloadsMatching;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.command.ManualReviewCommand;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingStatusChangedEvent;
import com.titanium.underwriting.exception.UnderwritingStatusException;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 转人工核保聚合语义测试（g02-03 / G02/AC-04）
 * <p>
 * 正反成对：① 正向——REVIEW 态可转人工，产出既有 {@link UnderwritingStatusChangedEvent}
 * （newStatus = MANUAL_REVIEW，reason 承载转人工意见），且该事件作为命令处理结果**同步返回**，
 * web 端点与 api 契约据此回执最新状态，无需回读读模型（投影有延迟）；
 * ② 反向——已出结论的终态核保件发起转人工必须被拒，且 {@code expectNoEvents()} 证明**未留下半截状态**。
 * </p>
 */
class UnderwritingManualReviewTest {

    private static final UnderwritingId UNDERWRITING_ID = new UnderwritingId("UW-001");
    private static final String         TENANT_ID       = "TENANT-001";
    private static final String         OPERATOR        = "uw01";
    private static final String         COMMENTS        = "保额超阈值，需人工复核";

    private FixtureConfiguration<Underwriting> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Underwriting.class);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void reviewCaseMovesToManualReviewAndReturnsStatusChangedSynchronously() {
        fixture.given(createdEvent(), statusChanged(UnderwritingEnum.UnderwritingStatus.REVIEW))
                .when(manualReviewCommand())
                .expectSuccessfulHandlerExecution()
                .expectResultMessageMatching(messageWithPayload(matches((UnderwritingStatusChangedEvent event) ->
                        event.underwritingId().equals(UNDERWRITING_ID)
                                && event.oldStatus() == UnderwritingEnum.UnderwritingStatus.REVIEW
                                && event.newStatus() == UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW
                                && COMMENTS.equals(event.reason()))))
                .expectEventsMatching(payloadsMatching(exactSequenceOf(
                        instanceOf(UnderwritingStatusChangedEvent.class))))
                .expectState(state -> assertEquals(UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW,
                        state.getStatus(), "转人工后聚合状态必须是 MANUAL_REVIEW"));
    }

    @Test
    void manualReviewOnConcludedCaseIsRejectedWithoutPublishingAnyEvent() {
        fixture.given(createdEvent(), standardDecision())
                .when(manualReviewCommand())
                .expectException(UnderwritingStatusException.class)
                .expectNoEvents();
    }

    private ManualReviewCommand manualReviewCommand() {
        return new ManualReviewCommand(UNDERWRITING_ID, COMMENTS, OPERATOR, TENANT_ID);
    }

    private UnderwritingCreatedEvent createdEvent() {
        return new UnderwritingCreatedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"), CustomerId.of("CUS-001"),
                UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, LocalDateTime.now(), OPERATOR, TENANT_ID, "PRD-001",
                "UW202401001", null);
    }

    private UnderwritingStatusChangedEvent statusChanged(UnderwritingEnum.UnderwritingStatus newStatus) {
        return new UnderwritingStatusChangedEvent(UNDERWRITING_ID, UnderwritingEnum.UnderwritingStatus.PENDING,
                newStatus, "流程流转", LocalDateTime.now(), OPERATOR, TENANT_ID);
    }

    /** 已出结论的终态（标准承保），用于反向用例 */
    private UnderwritingDecidedEvent standardDecision() {
        return new UnderwritingDecidedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"),
                UnderwritingEnum.RiskLevel.STANDARD, UnderwritingEnum.ConclusionType.ACCEPT,
                UnderwritingEnum.AuditType.AUTOMATIC, UnderwritingEnum.UnderwritingStatus.PENDING,
                UnderwritingEnum.UnderwritingStatus.STANDARD, 55, null, LocalDateTime.now(), OPERATOR, TENANT_ID, null,
                ProductConfigSource.CONFIGURED, null);
    }
}
