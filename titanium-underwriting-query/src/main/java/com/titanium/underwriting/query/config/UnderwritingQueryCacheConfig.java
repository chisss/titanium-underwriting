package com.titanium.underwriting.query.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 查询层缓存配置
 * 基于保险业务全生命周期核保承保阶段的查询性能优化需求
 * 符合项目规约的性能优化要求
 */
@Configuration
@EnableCaching
public class UnderwritingQueryCacheConfig {

    /**
     * 开发环境使用内存缓存
     */
    @Bean
    @Profile({"dev", "test"})
    public CacheManager devCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();

        // 配置缓存名称
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "underwriting:riskLevel",      // 风险等级查询缓存
            "underwriting:underwriter",    // 核保员查询缓存
            "underwriting:customerHistory", // 客户历史查询缓存
            "underwriting:statistics"      // 统计数据缓存
        ));

        return cacheManager;
    }

    /**
     * 生产环境使用Redis缓存（需要配置Redis连接）
     */
    @Bean
    @Profile("prod")
    public CacheManager prodCacheManager() {
        // 生产环境建议使用Redis缓存
        // 这里先使用内存缓存作为示例，实际生产环境需要配置RedisCacheManager
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();

        cacheManager.setCacheNames(java.util.Arrays.asList(
            "underwriting:riskLevel",
            "underwriting:underwriter",
            "underwriting:customerHistory",
            "underwriting:statistics"
        ));

        return cacheManager;
    }
}
