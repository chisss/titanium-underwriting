package com.titanium.underwriting.config;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.springboot.EventProcessorProperties;
import org.axonframework.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * 核保域事件处理器死信队列配置。
 *
 * <p>共享 JPA 事件存储兜底不会自动提供 DLQ provider，因此由核保服务组合根使用同一
 * EntityManager 与事务管理器装配 JPA 死信队列。</p>
 *
 * <p>🔴 <b>DLQ 必须走独立事务（{@code REQUIRES_NEW}），沿用投影事务则入队必被回滚</b>（D-501-26）：
 * 投影抛异常后事务已被标记 {@code rollback-only}，同一事务内的入队 INSERT 提交时一同回滚，
 * {@code axon_dead_letter_entry} 恒 0 行、token 位点不推进，整条读模型流被一条坏事件永久冻结。
 * 与 payment 域 {@code PaymentDeadLetterQueueConfiguration} 同构（该域有真实失败用例回归）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class UnderwritingDeadLetterQueueConfiguration {

    /**
     * 为配置中启用 DLQ 的处理组提供 JPA 持久化死信队列。
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterQueueProviderConfigurerModule.class)
    public DeadLetterQueueProviderConfigurerModule underwritingDeadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            EntityManagerProvider entityManagerProvider,
            PlatformTransactionManager platformTransactionManager,
            @Qualifier("serializer") Serializer genericSerializer,
            @Qualifier("eventSerializer") Serializer eventSerializer) {
        return new DeadLetterQueueProviderConfigurerModule(
                eventProcessorProperties,
                processingGroup -> configuration -> JpaSequencedDeadLetterQueue.<EventMessage<?>>builder()
                        .processingGroup(processingGroup)
                        .entityManagerProvider(entityManagerProvider)
                        .transactionManager(requiresNewTransactionManager(platformTransactionManager))
                        .genericSerializer(genericSerializer)
                        .eventSerializer(eventSerializer)
                        .build());
    }

    /**
     * 构造 DLQ 专用事务管理器：每次入队/重投都在新事务中执行并独立提交，不受投影事务
     * {@code rollback-only} 标记影响。
     */
    private static TransactionManager requiresNewTransactionManager(
            PlatformTransactionManager platformTransactionManager) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new SpringTransactionManager(platformTransactionManager, definition);
    }
}
