package com.titanium.underwriting.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import com.titanium.underwriting.common.constant.UnderwritingConstants;

/**
 * Kafka配置类
 */
@Configuration
public class KafkaConfig {

    private final String bootstrapServers;
    private final String consumerGroupId;
    /** 主题副本因子：**部署环境属性**，不是代码常量（见 {@link #underwritingDecidedTopic()} 的说明）。 */
    private final int    topicReplicationFactor;

    /** 构造注入 Kafka 配置项（构造器注入优先，禁用字段注入） */
    public KafkaConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                       @Value("${spring.kafka.consumer.group-id}") String consumerGroupId,
                       @Value("${kafka.topic.replication-factor:1}") int topicReplicationFactor) {
        this.bootstrapServers = bootstrapServers;
        this.consumerGroupId = consumerGroupId;
        this.topicReplicationFactor = topicReplicationFactor;
    }

    /**
     * 创建Kafka生产者配置
     * <p>
     * 🔴 <b>可靠性参数必须在此显式声明</b>：本域依赖裸 {@code spring-kafka}（非
     * {@code spring-boot-starter-kafka}），{@code KafkaProperties} 所在的 {@code spring-boot-kafka}
     * 自动配置模块不在类路径，Boot 的 Kafka 自动配置不激活，yml 里的 {@code spring.kafka.producer.*}
     * <b>没有任何消费者</b>——写了也不生效，读配置的人却会以为已配好。故生产者参数以本方法为唯一事实来源。
     * </p>
     * <ul>
     *   <li>{@code acks=all}：leader 需等全部同步副本确认，防 leader 切换时丢消息（Kafka 默认
     *       {@code acks=1} 只等 leader 本地写入，leader 随即崩溃即丢）；</li>
     *   <li>{@code enable.idempotence=true}：生产者幂等，重试不会产生重复消息（配合 acks=all 才可开启）；</li>
     *   <li>{@code retries}：瞬时故障（网络抖动、leader 选举）自动重试，避免直接落入死信队列。</li>
     * </ul>
     *
     * @return 生产者配置
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 发布器以 fastjson2 生成 JSON String 发送（UnderwritingKafkaEventPublisher），故 value 序列化器为
        // StringSerializer；原误配为 JacksonJsonDeserializer（反序列化器当序列化器用）会导致 Producer 创建失败、
        // 核保决策事件永不出站、policy 侧异步回流断裂。
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * 创建Kafka模板
     * @return Kafka模板
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Admin：消费本类声明的 {@link NewTopic} Bean 并由其在 broker 上创建主题。
     * <p>🔴 D-501-27：本域依赖裸 {@code spring-kafka}（自动配置模块 {@code spring-boot-kafka}
     * 不在类路径，见 {@link #producerFactory()} 说明），{@code KafkaAdmin} 从未被注册 ——
     * 下方 {@code underwritingDecidedTopic} 声明<b>静默失效</b>，broker 上的
     * {@code underwriting-decided} 实为生产者首次发送时 auto-create 所建，分区数不受本域掌控。
     * 实测佐证：声明 {@code partitions(3).replicas(2)}，broker 上实为 <b>1 分区 1 副本</b>。
     * 对齐 billing / claim / payment / regulatory 同名样板。</p>
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * 创建Kafka消费者配置
     * @return 消费者配置
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        configProps.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.titanium.underwriting.event");
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * 创建Kafka监听器容器工厂
     * @return 监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    /**
     * 创建核保决策事件主题（policy 域异步回流轨的消费来源，本域唯一外发主题）
     * <p>
     * 分区数显式声明 3 是<b>分区有序性保证的前提</b>：本主题的发布端以 {@code policyId} 为分区键
     * （见 {@code UnderwritingKafkaEventPublisher}），若交由 broker 按默认值自动建主题，分区数不可控，
     * 同投保单事件的落分区与保序性也就无从谈起。
     * </p>
     * <p>
     * 🔴 <b>D-501-27 实测佐证</b>：上述「分区数不可控」并非假设，而是<b>已发生的事实</b> ——
     * 本域缺 {@code KafkaAdmin}（见 {@link #kafkaAdmin()}），本声明长期静默失效，
     * broker 上该主题实为生产者首发送时 auto-create 所建，<b>1 分区 1 副本</b>（声明为 3 分区）。
     * 修复后由 KafkaAdmin 接管创建与扩容，分区数归位。
     * </p>
     * <p>
     * 🔴 原 {@code underwritingCreatedTopic} / {@code underwritingStatusChangedTopic} 两个 Bean
     * （连同其常量 {@code TOPIC_UNDERWRITING_CREATED}/{@code TOPIC_UNDERWRITING_STATUS_CHANGED}）
     * 建的主题自建起无任何发布点、亦无消费者，属死主题，已删除（m5-903）。
     * </p>
     *
     * @return 主题对象
     */
    @Bean
    public NewTopic underwritingDecidedTopic() {
        return TopicBuilder.name(UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED)
                .partitions(3)
                .replicas(topicReplicationFactor)
                .build();
    }
}
