package com.titanium.underwriting.config;

import javax.sql.DataSource;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Axon JPA 事件存储显式装配（组合根）。
 *
 * <h3>🔴 为什么必须显式声明（不是可选优化）</h3>
 * <p>
 * Spring Boot 4 把 {@code HibernateJpaAutoConfiguration} 从
 * {@code org.springframework.boot.autoconfigure.orm.jpa} 迁到
 * {@code org.springframework.boot.hibernate.autoconfigure}，而 Axon 4.10 的
 * {@code JpaAutoConfiguration}/{@code JpaEventStoreAutoConfiguration} 仍以
 * {@code @AutoConfigureAfter(旧包名)} 声明排序依赖。旧类在 Boot 4 中不存在 ⇒ 排序约束**静默失效**
 * ⇒ 这两个自动配置在 {@code EntityManagerFactory} 注册**之前**评估，其
 * {@code @ConditionalOnBean(EntityManagerFactory)} 恒不匹配 ⇒ {@code EntityManagerProvider} /
 * {@code EventStorageEngine} / {@code TokenStore} 全部缺失（启动期报
 * 「No qualifying bean of type EntityManagerProvider」，见 2026-10-19 条件评估报告实测）。
 * </p>
 * <p>
 * 取证（2026-10-19，可复跑）：{@code unzip -l ~/.m2/repository/org/springframework/boot/spring-boot-hibernate/4.0.7/spring-boot-hibernate-4.0.7.jar | grep HibernateJpaAutoConfiguration}
 * 只命中 {@code org/springframework/boot/hibernate/autoconfigure/HibernateJpaAutoConfiguration.class}；
 * 条件评估报告（{@code -Ddebug=true}）中 {@code JpaAutoConfiguration: did not match — @ConditionalOnBean
 * did not find any beans of type EntityManagerFactory}，而 {@code HibernateJpaAutoConfiguration} 最终 matched。
 * </p>
 * <p>
 * 本域 {@code UnderwritingDeadLetterQueueConfiguration} 依赖 JPA 版 {@code EntityManagerProvider} 装配
 * {@code JpaSequencedDeadLetterQueue}，故缺少本类时上下文直接启动失败（{@code underwriting-query-group} /
 * {@code underwriting-kafka-group} 的 DLQ 同时失效）。
 * </p>
 * <p>
 * 装配形态与 {@code titanium-policy}/{@code titanium-billing}/{@code titanium-product} 的同名配置类同构
 * （三域已先例）。若后续升级到修复了 Boot 4 包名的 Axon 版本，本类可移除——移除前须确认自动配置在
 * 条件评估报告中已为 matched，否则事件存储会静默回退到 JDBC 实现。
 * </p>
 */
@Configuration
public class AxonJpaEventStoreConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 容器管理的 EntityManager 提供者。
     */
    @Bean
    public EntityManagerProvider entityManagerProvider() {
        return () -> entityManager;
    }

    /**
     * Axon 事务管理器：显式绑定 Spring 的 {@link PlatformTransactionManager}（本域实际为 JPA 事务管理器）。
     * <p>
     * 🔴 不可依赖 Axon 的 {@code TransactionAutoConfiguration} 自动提供：它同样以 Boot 4 已迁包的旧类名声明
     * {@code @AutoConfigureAfter}，评估早于 JPA 自动配置注册 {@code transactionManager} 的时机，其
     * {@code @ConditionalOnBean(PlatformTransactionManager)} 遂不匹配，随后由
     * {@code NoOpTransactionAutoConfiguration} 兜底注入 {@code NoOpTransactionManager}。
     * 2026-10-19 条件评估报告实测：{@code TransactionAutoConfiguration#axonTransactionManager} did not match —
     * 「did not find any beans of type PlatformTransactionManager」，而
     * {@code NoOpTransactionAutoConfiguration#axonTransactionManager} matched。
     * </p>
     * <p>
     * NoOp 下 {@code JpaEventStorageEngine.appendEvents} 不在事务内执行，真实写事件即抛
     * {@code TransactionRequiredException: No EntityManager with actual transaction available for current thread}
     * （payment 域 {@code PaymentDeadLetterResilienceTest} 首次真实写事件时暴露，本域同步修复）。
     * </p>
     */
    @Bean
    public TransactionManager axonTransactionManager(PlatformTransactionManager platformTransactionManager) {
        return new SpringTransactionManager(platformTransactionManager);
    }

    /**
     * 持久化异常解析器（MySQL SQL 错误码映射，供事件存储判定并发冲突等可重试异常）。
     */
    @Bean
    public PersistenceExceptionResolver persistenceExceptionResolver(DataSource dataSource) {
        try {
            return new SQLErrorCodesResolver(dataSource);
        } catch (Exception e) {
            throw new IllegalStateException("创建 PersistenceExceptionResolver 失败", e);
        }
    }

    /**
     * 基于 JPA 的事件存储引擎（事件落 {@code axon_domain_event_entry}，表名前缀由
     * {@code AxonTablePrefixPhysicalNamingStrategy} 在运行时重定向）。
     */
    @Bean
    public EventStorageEngine eventStorageEngine(
            Serializer eventSerializer,
            PersistenceExceptionResolver persistenceExceptionResolver,
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager) {
        return JpaEventStorageEngine.builder()
                .snapshotSerializer(eventSerializer)
                .eventSerializer(eventSerializer)
                .persistenceExceptionResolver(persistenceExceptionResolver)
                .snapshotFilter(e -> true)
                .transactionManager(transactionManager)
                .entityManagerProvider(entityManagerProvider)
                .build();
    }

    /**
     * 事件处理器进度存储（tracking 处理组 {@code underwriting-query-group} /
     * {@code underwriting-kafka-group} 的位点必须持久化，否则重启后重放全量事件）。
     */
    @Bean
    public TokenStore tokenStore(EntityManagerProvider entityManagerProvider, Serializer serializer) {
        return JpaTokenStore.builder()
                .entityManagerProvider(entityManagerProvider)
                .serializer(serializer)
                .build();
    }
}
