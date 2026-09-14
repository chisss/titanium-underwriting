package com.titanium.underwriting.port.product;

import java.math.BigDecimal;
import java.util.Objects;

import com.titanium.underwriting.common.enums.ProductConfigSource;

/**
 * 产品核保配置出口端口（driven port，与聚合平级，按对端域 product 拆子包）
 * <p>
 * 核保域在执行决策时，从产品域读取该险种的核保策略配置（阈值、加费许可、规则集编码等），
 * 替代聚合内硬编码常量，实现核保因子配置化（UW-4）。
 * 由 infrastructure 的 {@code ProductUnderwritingConfigAdapter} 经 Feign 调用产品域实现。
 * </p>
 */
public interface ProductUnderwritingConfigPort {

    /**
     * 按产品编码获取核保配置快照
     * <p>
     * 🔴 返回的 {@link ProductUnderwritingConfig#configSource()} 标明本次配置的<b>真实来源</b>，
     * 调用方不得把返回对象当作「产品确实配了核保策略」——兜底配置同样非 null，须以
     * {@link ProductConfigSource#configured()} 判定。
     * </p>
     *
     * @param productCode 产品/险种编码
     * @param tenantId    租户ID
     * @return 核保配置快照（含配置来源标记）
     */
    ProductUnderwritingConfig fetchConfig(String productCode, String tenantId);

    /**
     * 产品核保配置快照（domain值对象，屏蔽产品域DTO细节）
     *
     * @param surchargeAcceptable         是否支持加费承保；false时核保决策不产出加费
     * @param manualReviewAmountThreshold 转人工核保的保额阈值（无输入兜底路径使用，null表示不限）
     * @param ruleSetCode                 关联的规则引擎规则集编码（dev-505）；null/空表示未接入规则引擎，
     *                                    核保域回退内置评分逻辑
     * @param configSource                配置来源（m11-1404）：标明上述三项取值是产品域真实返回，
     *                                    还是兜底默认值、以及兜底的具体处境
     */
    record ProductUnderwritingConfig(boolean surchargeAcceptable, BigDecimal manualReviewAmountThreshold,
                                     String ruleSetCode, ProductConfigSource configSource) {

        public ProductUnderwritingConfig {
            Objects.requireNonNull(configSource, "配置来源不可为空——来源不明则「没配」与「取不到」再次不可区分");
        }

        /**
         * 兜底默认配置：允许加费，无金额阈值限制，未接入规则引擎。
         * <p>
         * 🔴 <b>{@code surchargeAcceptable = true} 是过渡期兼容决策，不是业务判断</b>：历史上该默认值
         * 与「产品显式配置为允许加费」共用同一取值，改值会改变存量核保判定结果（可能由「加费承保」
         * 变为「拒保」），属破坏性变更，故本次只<b>显式化来源</b>而不改取值。待产品域核保策略配置
         * 全面落地后，再评估是否收紧为「无配置即不加费」。
         * </p>
         *
         * @param configSource 兜底的真实处境（{@link ProductConfigSource#NOT_CONFIGURED} 或
         *                     {@link ProductConfigSource#UNAVAILABLE}），二者不可混同
         * @return 兜底配置
         */
        public static ProductUnderwritingConfig defaultConfig(ProductConfigSource configSource) {
            return new ProductUnderwritingConfig(true, null, null, configSource);
        }

        /**
         * 是否已接入规则引擎（规则集编码非空）。
         *
         * @return true 表示走规则引擎核保链路
         */
        public boolean ruleEngineEnabled() {
            return ruleSetCode != null && !ruleSetCode.isBlank();
        }

        /**
         * 本快照各项取值是否有产品策略依据。
         *
         * @return true 表示产品域确实返回了配置（非兜底）
         */
        public boolean configured() {
            return configSource.configured();
        }
    }
}
