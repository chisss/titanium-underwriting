package com.titanium.underwriting.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.UnderwritingStatus;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.valueobject.ExtraPremium;
import com.titanium.underwriting.valueobject.InsuranceId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保单事件流只读出口的 Axon 实现测试（g02-04 / AC-05 幂等的强一致数据来源）
 * <p>
 * 锁死两组语义：
 * <ul>
 *   <li>{@code findLatestDecision}——① 多次决策取<b>最后一条</b>（保全加保重新核保后，早先的结论不是当前结论）；
 *       ② 尚未出具结论时返回空（编排器据此判「确属在办」而续跑）；③ 读取失败时<b>降级为空而非抛出</b>。</li>
 *   <li>{@code exists}——④ 有事件即「已建」；⑤ 空流即「未建」（<b>依据 Axon 契约「事件存储中没有该聚合的事件
 *       则返回空流」</b>，不是靠捕获异常）；⑥ 🔴 读取失败时<b>按「已存在」处理</b>——判为不存在会让编排器
 *       静默再建一张核保单，判为存在则后续命令显式失败，<b>宁可报错，不可静默重复</b>。</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EventStoreUnderwritingEventStreamRepositoryTest {

    private static final String UNDERWRITING_ID = "229679516912451584";
    private static final String TENANT_ID       = "TEST-TENANT-001";

    @Mock
    private EventStore eventStore;

    @InjectMocks
    private EventStoreUnderwritingEventStreamRepository repository;

    // ------------------------------------------------------------------ findLatestDecision

    @Test
    void returnsLatestDecisionWhenCaseWasDecidedMoreThanOnceSoEarlierConclusionNeverWins() {
        when(eventStore.readEvents(UNDERWRITING_ID)).thenReturn(DomainEventStream.of(
                message(0, "UnderwritingCreatedEvent"),
                message(1, decision("拒保结论")),
                message(2, decision("重核保后的加费承保结论"))));

        Optional<UnderwritingDecidedEvent> latest = repository.findLatestDecision(UnderwritingId.of(UNDERWRITING_ID));

        assertTrue(latest.isPresent());
        assertEquals("重核保后的加费承保结论", latest.get().reason());
    }

    @Test
    void returnsEmptyWhenCaseHasNoDecisionYetSoOrchestratorCanResumeIt() {
        when(eventStore.readEvents(UNDERWRITING_ID)).thenReturn(DomainEventStream.of(
                message(0, "UnderwritingCreatedEvent"),
                message(1, "UnderwritingInputSubmittedEvent")));

        assertTrue(repository.findLatestDecision(UnderwritingId.of(UNDERWRITING_ID)).isEmpty());
    }

    @Test
    void returnsEmptyInsteadOfThrowingWhenEventStreamIsUnreadableSoAutoDecisionStaysAvailable() {
        when(eventStore.readEvents(anyString())).thenThrow(new IllegalStateException("事件存储不可达"));

        assertTrue(repository.findLatestDecision(UnderwritingId.of(UNDERWRITING_ID)).isEmpty());
    }

    @Test
    void returnsEmptyForMissingIdWithoutTouchingEventStore() {
        assertTrue(repository.findLatestDecision(null).isEmpty());
        assertTrue(repository.findLatestDecision(UnderwritingId.of(null)).isEmpty());
    }

    // ------------------------------------------------------------------ exists

    @Test
    void reportsExistingWhenAggregateHasEventsSoReplayNeverCreatesSecondCase() {
        when(eventStore.readEvents(UNDERWRITING_ID)).thenReturn(DomainEventStream.of(
                message(0, "UnderwritingCreatedEvent"),
                message(1, decision("加费承保结论"))));

        assertTrue(repository.exists(UnderwritingId.of(UNDERWRITING_ID)));
    }

    @Test
    void reportsMissingWhenStreamIsEmptySoTheFirstCallStillCreatesTheCase() {
        // Axon 契约：事件存储中没有该聚合的事件时返回**空流**（非抛异常）——「不存在」由空流表达。
        // 本用例是该契约的可执行留痕：若哪天换成会抛异常的存储实现，此例即失败，提醒改走 catch 分支。
        when(eventStore.readEvents(UNDERWRITING_ID)).thenReturn(DomainEventStream.empty());

        assertFalse(repository.exists(UnderwritingId.of(UNDERWRITING_ID)));
    }

    @Test
    void reportsExistingInsteadOfMissingWhenStreamIsUnreadableSoFailureNeverSilentlyCreatesDuplicate() {
        // 🔴 降级方向是被测语义本身：读不出时判「不存在」⇒ 编排器新建第二张核保单（业务上不可接受的重复核保）；
        // 判「已存在」⇒ 编排器走续跑，由后续命令以显式异常失败，调用方可重试且无脏数据。
        // 本用例锁死后者——把返回值改成 false 必须让此例变红。
        when(eventStore.readEvents(anyString())).thenThrow(new IllegalStateException("事件存储不可达"));

        assertTrue(repository.exists(UnderwritingId.of(UNDERWRITING_ID)));
    }

    @Test
    void reportsMissingForMissingIdWithoutTouchingEventStore() {
        assertFalse(repository.exists(null));
        assertFalse(repository.exists(UnderwritingId.of(null)));
    }

    // ------------------------------------------------------------------ 夹具

    private DomainEventMessage<?> message(long sequence, Object payload) {
        return new GenericDomainEventMessage<>("Underwriting", UNDERWRITING_ID, sequence, payload);
    }

    private UnderwritingDecidedEvent decision(String reason) {
        return new UnderwritingDecidedEvent(new UnderwritingId(UNDERWRITING_ID), PolicyId.of("INS-001"),
                UnderwritingEnum.RiskLevel.SUB_STANDARD, ConclusionType.MODIFY, UnderwritingEnum.AuditType.AUTOMATIC,
                UnderwritingStatus.PENDING, UnderwritingStatus.RATED, 55,
                ExtraPremium.ofRatio(new BigDecimal("0.30"), null, "高血压加费"), LocalDateTime.now(), "SYSTEM",
                TENANT_ID, reason, ProductConfigSource.CONFIGURED, InsuranceId.of("INS-001"));
    }
}
