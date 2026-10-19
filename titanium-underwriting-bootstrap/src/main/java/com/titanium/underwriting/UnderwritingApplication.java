package com.titanium.underwriting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 核保服务启动类（组合根）
 * <p>
 * 写侧为纯事件溯源（无 JPA 写表），JPA 仅扫描 CQRS 读侧读模型/仓储（query.view / query.repository）；
 * 开启定时任务以驱动读侧死信队列（DLQ）重试，保障核保读模型投影最终一致。
 * </p>
 * <p>
 * 🔴 {@code @EntityScan} 必须显式列出 Axon JPA 事件存储的实体包：显式声明后 Spring Boot 只扫描所列包、
 * 不再回落默认包（本域 {@code com.titanium.underwriting}），而 JPA 事件存储/令牌/死信分别依赖
 * {@code DomainEventEntry}、{@code TokenEntry}、{@code DeadLetterEntry} 等实体；遗漏则真实写事件时
 * 报「Unknown entity」（测试环境 {@code ddl-auto=create-drop} 也不会建对应表）。与
 * {@code AxonJpaEventStoreConfig} 同批生效。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
// 额外扫 com.titanium.common：核保案号发号依赖共享内核 JdbcBusinessNumberGenerator（对齐 customer/clause 域样板）
@ComponentScan(basePackages = { "com.titanium.underwriting", "com.titanium.common" })
@EnableFeignClients(basePackages = { "com.titanium.underwriting.infrastructure.client",
        "com.titanium.product.api", "com.titanium.ruleengine.api", "com.titanium.featurecenter.api" })
// 写侧收敛为纯事件溯源，JPA 仅承载 CQRS 读模型（query.view / query.repository）
@EntityScan(basePackages = {
        "com.titanium.underwriting.query.view",
        // Axon JPA 事件存储 / 令牌存储 / 死信队列实体（见 AxonJpaEventStoreConfig）
        "org.axonframework.eventsourcing.eventstore.jpa",
        "org.axonframework.eventhandling.tokenstore.jpa",
        "org.axonframework.eventhandling.deadletter.jpa"
})
@EnableJpaRepositories(basePackages = {
        "com.titanium.underwriting.query.repository"
})
public class UnderwritingApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnderwritingApplication.class, args);
    }
}
