package com.titanium.underwriting.port;

import java.math.BigDecimal;

/**
 * 产品核保配置出口端口（driven port，与聚合平级）
 * <p>
 * 核保域在执行决策时，从产品域读取该险种的核保策略配置（阈值、加费许可等），
 * 替代聚合内硬编码常量，实现核保因子配置化（UW-4）。
 * 由 infrastructure 的 {@code ProductUnderwritingConfigAdapter} 经 Feign 调用产品域实现。
 * </p>
 */
public interface ProductUnderwritingConfigPort {

    /**
     * 按产品编码获取核保配置快照
     * <p>
     * productCode 为 null 或产品不存在时，返回默认配置（兜底，不阻断核保流程）。
     * </p>
     *
     * @param productCode 产品/险种编码
     * @param tenantId    租户ID
     * @return 核保配置快照
     */
    ProductUnderwritingConfig fetchConfig(String productCode, String tenantId);

    /**
     * 产品核保配置快照（domain值对象，屏蔽产品域DTO细节）
     *
     * @param surchargeAcceptable        是否支持加费承保；false时核保决策不产出加费
     * @param manualReviewAmountThreshold 转人工核保的保额阈值（无输入兜底路径使用，null表示不限）
     */
    record ProductUnderwritingConfig(boolean surchargeAcceptable, BigDecimal manualReviewAmountThreshold) {

        /** 默认配置：允许加费，无金额阈值限制 */
        public static ProductUnderwritingConfig defaultConfig() {
            return new ProductUnderwritingConfig(true, null);
        }
    }
}
