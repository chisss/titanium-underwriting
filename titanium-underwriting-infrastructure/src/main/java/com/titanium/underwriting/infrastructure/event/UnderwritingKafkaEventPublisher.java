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
        String key = event.underwritingId() != null ? event.underwritingId().value() : null;
        String eventJson = JSON.toJSONString(event);
        log.info("发布核保决策事件到 Kafka, underwritingId={}, policyId={}, conclusion={}", key,
                event.policyId() != null ? event.policyId().value() : null, event.conclusionType());
        kafkaTemplate.send(UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED, key, eventJson);
    }
}
