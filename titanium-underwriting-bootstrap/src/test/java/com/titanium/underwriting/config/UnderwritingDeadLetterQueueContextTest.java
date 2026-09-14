package com.titanium.underwriting.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.springboot.autoconfig.JpaAutoConfiguration;
import org.axonframework.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.ConditionAndOutcomes;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import com.titanium.underwriting.UnderwritingApplication;
import com.titanium.underwriting.common.constant.UnderwritingConstants;

@SpringBootTest(classes = UnderwritingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:underwriting-dlq;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.liquibase.enabled=false",
                "spring.cloud.openfeign.client.config.productApi.url=http://localhost",
                "spring.cloud.openfeign.client.config.ruleEngineApi.url=http://localhost",
                "spring.cloud.openfeign.client.config.featureCenterApi.url=http://localhost",
                "axon.axonserver.enabled=false"
        })
class UnderwritingDeadLetterQueueContextTest {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private EventProcessingConfiguration eventProcessingConfiguration;

    @Test
    void providesUnderwritingDeadLetterProcessorWhenJpaAutoConfigurationDoesNotMatch() {
        Map<String, DeadLetterQueueProviderConfigurerModule> providers =
                applicationContext.getBeansOfType(DeadLetterQueueProviderConfigurerModule.class);
        ConditionEvaluationReport report = ConditionEvaluationReport
                .get(applicationContext.getBeanFactory());
        ConditionAndOutcomes outcomes = report.getConditionAndOutcomesBySource()
                .get(JpaAutoConfiguration.class.getName());

        assertEquals(1, providers.size());
        assertEquals(Set.of("underwritingDeadLetterQueueProviderConfigurerModule"), providers.keySet());
        assertNotNull(outcomes);
        assertFalse(outcomes.isFullMatch());
        assertTrue(eventProcessingConfiguration
                .sequencedDeadLetterProcessor("underwriting-query-group")
                .isPresent());
    }

    /**
     * 跨域外发处理组必须以 <b>tracking</b> 模式运行，且启用死信队列。
     * <p>
     * 🔴 守护点（m6-909 改造后）：原先本组为 {@code subscribing}——事件到达即同步发布，看似更简单，
     * 但死信队列 {@code SequencedDeadLetterQueue} <b>只支持流式处理器</b>（tracking / pooled-streaming），
     * subscribing 拿不到 DLQ：Kafka 不可达时发布失败既无重投也无留痕，
     * {@code underwriting-decided} 这条 policy 回写核保结论的唯一异步通道即永久断链
     * （投保单永远停在待核保）。
     * </p>
     * <p>
     * 三条断言各有分工：① 组已注册——{@code @ProcessingGroup} 的值与 application.yml 中
     * {@code axon.eventhandling.processors.<name>} 的键必须一致，漂移时处理器仍存在（不报错）却退回默认配置；
     * ② 是 tracking——形态是 DLQ 的前提；③ DLQ 可获取——{@code dlq.enabled: true} 真正生效，
     * 这是 {@code DeadLetterQueueService} 重投的前提（拿不到 processor 时该方法只记 debug 日志后返回，
     * 从外部看是静默空转）。
     * </p>
     * <p>
     * ⚠️ tracking 的已知代价是「首启位点」问题：令牌不存在时会从事件流<b>头部</b>开始消费。
     * 该风险已由 application.yml 的 {@code titanium.axon.outbound-relay.groups} 登记消解——
     * 该配置令本组首启位点取事件流<b>末端</b>。故改动本组配置时须同步检查该登记项，二者是一组。
     * </p>
     */
    @Test
    void providesTrackingKafkaPublisherProcessingGroupWithDeadLetterQueue() {
        var processor = eventProcessingConfiguration
                .eventProcessorByProcessingGroup(UnderwritingConstants.KAFKA_PROCESSING_GROUP);
        assertTrue(processor.isPresent(),
                "未注册处理组 " + UnderwritingConstants.KAFKA_PROCESSING_GROUP
                        + "：@ProcessingGroup 与 application.yml 的 processors 键不一致");
        assertInstanceOf(TrackingEventProcessor.class, processor.get(),
                "处理组 " + UnderwritingConstants.KAFKA_PROCESSING_GROUP
                        + " 未按 tracking 模式装配：subscribing 拿不到死信队列，发布失败即永久丢失");

        assertTrue(
                eventProcessingConfiguration
                        .sequencedDeadLetterProcessor(UnderwritingConstants.KAFKA_PROCESSING_GROUP).isPresent(),
                "处理组 " + UnderwritingConstants.KAFKA_PROCESSING_GROUP
                        + " 未启用死信队列：需在 application.yml 该组下配 dlq.enabled=true，"
                        + "否则 DeadLetterQueueService 静默空转、失败事件永不重投");
    }
}
