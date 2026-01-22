package com.titanium.underwriting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.titanium.underwriting.infrastructure.client")
@EntityScan(basePackages = "com.titanium.underwriting.infrastructure.entity")
@EnableJpaRepositories(basePackages = "com.titanium.underwriting.infrastructure.repository.jpa")
public class UnderwritingApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnderwritingApplication.class, args);
    }
}
