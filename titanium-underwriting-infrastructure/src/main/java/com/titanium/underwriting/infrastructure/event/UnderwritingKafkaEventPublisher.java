package com.titanium.underwriting.infrastructure.event;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.underwriting.common.constant.UnderwritingConstants;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保决策事件 Kafka 发布器（异步回流）
 * <p>
 * 订阅核保域 {@link UnderwritingDecidedEvent}，序列化为 JSON 外发到 Kafka，供 policy 域异步消费回写核保结论。
 * 这是核保结论「双轨回流」的异步轨：人工核保/批量核保等无法同步即时产出结论的场景，由本发布器推送，
 * policy 侧监听后经 {@code receiveUnderwritingResult} 回写投保单聚合。同步轨（投保出单主链路）仍走
 * policy 的 {@code UnderwritingDecisionGateway} 即时拉取。
 * </p>
 * <p>
 * <b>分区键取 {@code policyId}</b>：消费端 policy 域 {@code UnderwritingDecidedEventListener} 正是按投保单
 * 维度回写聚合，而同一投保单会产生<b>多次</b>核保决策（拒保后重投、保全加保的重新核保）——只有以
 * {@code policyId} 分区，这些事件的落分区才一致，Kafka 的「同分区内保序」才能兑现为「同投保单内保序」。
 * 改用 {@code underwritingId} 或任其为 null 都会把同一投保单的事件散到不同分区（Kafka 对 null key
 * 更是在可用分区间轮询），消费端据此乱序回写，后到的旧结论可能覆盖新结论。
 * </p>
 * <p>
 * 以聚合内进程内总线（simple）投递给本处理器，再经 spring-kafka 外发；处理组
 * {@code underwriting-kafka-group} 与写侧/读侧隔离。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup(UnderwritingConstants.KAFKA_PROCESSING_GROUP)
@RequiredArgsConstructor
public class UnderwritingKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @EventHandler
    public void on(UnderwritingDecidedEvent event) {
        String underwritingId = event.underwritingId() != null ? event.underwritingId().value() : null;
        String policyId = event.policyId() != null ? event.policyId().value() : null;
        String partitionKey = policyId;
        if (partitionKey == null) {
            // 理论不可达：CreateUnderwritingCommand 强制 underwritingId/policyId 非空
            // （Underwriting#validateCreateCommand），仅旧事件流可能缺字段。
            // 此时无法按投保单分区，退化为按核保单分区以保住「同一核保内保序」，并告警暴露数据异常。
            partitionKey = underwritingId;
            log.warn("核保决策事件缺少 policyId，退化按 underwritingId 分区，同一投保单的多次决策无法保证有序: "
                    + "underwritingId={}", partitionKey);
        }
        String eventJson = JSON.toJSONString(event);
        log.info("发布核保决策事件到 Kafka, partitionKey={}, underwritingId={}, policyId={}, conclusion={}", partitionKey,
                underwritingId, policyId, event.conclusionType());
        kafkaTemplate.send(UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED, partitionKey, eventJson);
    }
}
