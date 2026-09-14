package com.titanium.underwriting.infrastructure.event;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

import com.titanium.common.kafka.KafkaPublishSupport;
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
 * <p>
 * 🔴 <b>处理组形态（m6-909 改造）</b>：处理组 {@value UnderwritingConstants#KAFKA_PROCESSING_GROUP}
 * 原为 {@code subscribing} 模式（事件到达即同步发布、不经事件存储）；现改为 <b>{@code tracking} +
 * {@code dlq.enabled}</b>——死信队列只支持流式处理器，subscribing 拿不到 DLQ，发布失败即永久丢失
 * （核保结论不回传，投保单永远停在待核保）。改为 tracking 后首启位点由
 * {@code titanium.axon.outbound-relay.groups} 登记为<b>事件流末端</b>（避免全新部署重放全量历史核保事件）。
 * </p>
 * <p>
 * 🔴 <b>线格式</b>：载荷是 fastjson2 生成的 JSON <b>字符串</b>，经 {@code KafkaTemplate<String,Object>} 与
 * 判据处显式配置的 {@code StringSerializer} 逐字节透传。<b>不得</b>改为发 POJO（value serializer 会再编码
 * 一次，下游 {@code JSONObject.parseObject} 解析出转义字符串、字段取值恒 null）。
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
        // 分区键解析并入私有方法：本方法内对 partitionKey 只发生一次赋值，既是 lambda 捕获所要求的
        // effectively final，又让发送点第 2 实参保持为 partitionKey——事件目录「分区键」列登记的正是该
        // 表达式，CrossDomainEventCatalogTest 按源码文本逐字比对，改名即判定分区键漂移而失败。
        String partitionKey = resolvePartitionKey(policyId, underwritingId);
        String eventJson = JSON.toJSONString(event);
        log.info("发布核保决策事件到 Kafka, partitionKey={}, underwritingId={}, policyId={}, conclusion={}", partitionKey,
                underwritingId, policyId, event.conclusionType());

        KafkaPublishSupport.awaitSent(
                () -> kafkaTemplate.send(UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED, partitionKey, eventJson),
                UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED, partitionKey);
    }

    /**
     * 解析分区键：优先 {@code policyId}（同一投保单的多次决策须落同一分区方能保序），缺失时退化为
     * {@code underwritingId} 并告警暴露数据异常。
     *
     * @param policyId       投保单ID，正常路径下的分区键
     * @param underwritingId 核保单ID，退化路径下的分区键
     * @return 实际用于分区的键
     */
    private String resolvePartitionKey(String policyId, String underwritingId) {
        if (policyId != null) {
            return policyId;
        }
        // 理论不可达：CreateUnderwritingCommand 强制 underwritingId/policyId 非空
        // （Underwriting#validateCreateCommand），仅旧事件流可能缺字段。
        // 此时无法按投保单分区，退化为按核保单分区以保住「同一核保内保序」，并告警暴露数据异常。
        log.warn("核保决策事件缺少 policyId，退化按 underwritingId 分区，同一投保单的多次决策无法保证有序: underwritingId={}",
                underwritingId);
        return underwritingId;
    }
}
