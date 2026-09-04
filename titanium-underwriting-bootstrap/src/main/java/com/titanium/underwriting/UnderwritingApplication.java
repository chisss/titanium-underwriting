package com.titanium.underwriting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 核保服务启动类（组合根）
 * <p>
 * 写侧为纯事件溯源（无 JPA 写表），JPA 仅扫描 CQRS 读侧读模型/仓储（query.view / query.repository）；
 * 开启定时任务以驱动读侧死信队列（DLQ）重试，保障核保读模型投影最终一致。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = { "com.titanium.underwriting.infrastructure.client",
        "com.titanium.product.api", "com.titanium.ruleengine.api", "com.titanium.featurecenter.api" })
// 写侧收敛为纯事件溯源，JPA 仅承载 CQRS 读模型（query.view / query.repository）
@EntityScan(basePackages = {
        "com.titanium.underwriting.query.view"
})
@EnableJpaRepositories(basePackages = {
        "com.titanium.underwriting.query.repository"
})
public class UnderwritingApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnderwritingApplication.class, args);
    }
}
