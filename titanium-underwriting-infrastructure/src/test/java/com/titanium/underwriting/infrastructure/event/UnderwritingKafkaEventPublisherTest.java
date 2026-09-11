package com.titanium.underwriting.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.common.constant.UnderwritingConstants;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保决策事件 Kafka 发布器单元测试（m0-713）
 * <p>
 * 锁死分区键语义：<b>按投保单（policyId）分区</b>。消费端 policy 域按投保单维度回写聚合，而同一投保单会
 * 产生多次核保决策（拒保后重投、保全加保的重新核保），只有分区键一致，Kafka 的「同分区内保序」才能兑现为
 * 「同投保单内保序」。回归目标有三：① 不同核保单、同一投保单的决策落同一分区键；② policyId 缺失时退化
 * 为 underwritingId 而非 null（null key 会轮询分区，彻底丧失保序）；③ 事件仍投递到既定 topic 且载荷含
 * policyId，供下游回流取用。
 * </p>
 */
class UnderwritingKafkaEventPublisherTest {

    private static final String POLICY_ID = "POLICY_001";

    @Test
    @DisplayName("【核心】分区键取 policyId：同一投保单的多次决策必须落同一分区")
    void shouldPartitionByPolicyIdAcrossMultipleDecisions() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UnderwritingKafkaEventPublisher publisher = new UnderwritingKafkaEventPublisher(kafkaTemplate);

        // 同一投保单的两次核保决策：underwritingId 不同（拒保后重投会新建核保单），policyId 相同
        publisher.on(decided("UW_001", POLICY_ID));
        publisher.on(decided("UW_002", POLICY_ID));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(eq(UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED), keyCaptor.capture(),
                anyString());
        assertEquals(List.of(POLICY_ID, POLICY_ID), keyCaptor.getAllValues(),
                "同一投保单的多次决策必须以 policyId 作分区键，用 underwritingId 会把它们散到不同分区");
    }

    @Test
    @DisplayName("policyId 缺失时退化为 underwritingId 分区，绝不发送 null key")
    void shouldFallBackToUnderwritingIdWhenPolicyIdMissing() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UnderwritingKafkaEventPublisher publisher = new UnderwritingKafkaEventPublisher(kafkaTemplate);

        publisher.on(decided("UW_003", null));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED), keyCaptor.capture(),
                anyString());
        assertNotNull(keyCaptor.getValue(), "Kafka 对 null key 轮询分区，绝不能发送 null key");
        assertEquals("UW_003", keyCaptor.getValue(), "无法按投保单分区时退化为按核保单分区，保住核保单内保序");
    }

    @Test
    @DisplayName("载荷投递到核保决策 topic 且含 policyId，供 policy 域回流取用")
    void shouldPublishPayloadCarryingPolicyId() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UnderwritingKafkaEventPublisher publisher = new UnderwritingKafkaEventPublisher(kafkaTemplate);

        publisher.on(decided("UW_004", POLICY_ID));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED), eq(POLICY_ID),
                payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains(POLICY_ID), "载荷须携带 policyId，否则消费端无法定位投保单，实际: " + payload);
        assertTrue(payload.contains(UnderwritingEnum.ConclusionType.ACCEPT.getCode()),
                "载荷须携带核保结论，实际: " + payload);
    }

    /**
     * 构造核保决策事件：只关心中枢字段，其余取固定值。
     *
     * @param underwritingId 核保单标识
     * @param policyId       投保单标识，传 null 模拟旧事件流缺字段
     */
    private UnderwritingDecidedEvent decided(String underwritingId, String policyId) {
        return new UnderwritingDecidedEvent(
                UnderwritingId.of(underwritingId),
                policyId != null ? PolicyId.of(policyId) : null,
                UnderwritingEnum.RiskLevel.STANDARD,
                UnderwritingEnum.ConclusionType.ACCEPT,
                UnderwritingEnum.AuditType.AUTOMATIC,
                UnderwritingEnum.UnderwritingStatus.PENDING,
                UnderwritingEnum.UnderwritingStatus.APPROVED,
                80,
                null,
                LocalDateTime.of(2026, 9, 11, 10, 0),
                "UW_USER",
                "TENANT_001",
                null);
    }
}
