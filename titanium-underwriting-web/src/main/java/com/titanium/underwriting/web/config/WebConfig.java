package com.titanium.underwriting.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.titanium.underwriting.web.interceptor.TenantInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * Web配置类
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册租户拦截器，拦截所有API请求
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**");
    }
}
