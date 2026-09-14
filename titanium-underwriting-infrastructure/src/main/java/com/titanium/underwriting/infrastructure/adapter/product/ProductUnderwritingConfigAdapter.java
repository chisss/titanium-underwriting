package com.titanium.underwriting.infrastructure.adapter.product;

import org.springframework.stereotype.Component;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.response.config.UnderwritingConfigResponse;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 产品核保配置适配器（driven adapter，位于 infrastructure，按对端域 product 拆子包）
 * <p>
 * 实现 {@link ProductUnderwritingConfigPort}，经 Feign {@link ProductApi} 调用产品域
 * {@code /{productId}/underwriting-config} 端点，将强类型契约解析为
 * {@link ProductUnderwritingConfig} 值对象返回给 application 层。
 * 产品域不可用时兜底返回默认配置（允许加费，不限金额，未接入规则引擎），不阻断核保流程。
 * </p>
 * <p>
 * 🔴 <b>三处兜底各自标注真实来源（m11-1404）</b>：无产品编码 → {@code NOT_CONFIGURED}（「没配」）、
 * 产品域返回不可用 / 调用异常 → {@code UNAVAILABLE}（「取不到」）。此前三处一律返回同一个无来源标记的
 * 默认对象，使「产品没配核保策略」与「产品域挂了」在系统内不可区分，更与「产品显式配置为允许加费」
 * 不可区分。标注来源不改变取值：三态下 {@code surchargeAcceptable} 仍为 true（存量兼容）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductUnderwritingConfigAdapter implements ProductUnderwritingConfigPort {

    /** 历史字段 autoApprovalCondition 中承载规则集编码的前缀（如 ruleSet:UW_STD_001） */
    private static final String LEGACY_RULE_SET_PREFIX = "ruleSet:";

    private final ProductApi productApi;

    @Override
    public ProductUnderwritingConfig fetchConfig(String productCode, String tenantId) {
        if (productCode == null || productCode.isBlank()) {
            log.warn("[核保配置] 未提供产品编码，无从查询产品核保策略，按默认配置决策: configSource={}, tenantId={}",
                    ProductConfigSource.NOT_CONFIGURED, tenantId);
            return ProductUnderwritingConfig.defaultConfig(ProductConfigSource.NOT_CONFIGURED);
        }
        try {
            ApiResponse<UnderwritingConfigResponse> response = productApi.getUnderwritingConfigByCode(productCode, tenantId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("[核保配置] 产品核保配置不可用，按默认配置决策: configSource={}, productCode={}",
                        ProductConfigSource.UNAVAILABLE, productCode);
                return ProductUnderwritingConfig.defaultConfig(ProductConfigSource.UNAVAILABLE);
            }
            // 契约已为强类型 record，直接取访问器，禁止 JSON 往返弱类型解析（禁 Map/JSONObject 接契约）
            UnderwritingConfigResponse config = response.getData();
            return new ProductUnderwritingConfig(config.surchargeAcceptable(), config.manualReviewAmountThreshold(),
                    resolveRuleSetCode(config.ruleSetCode(), config.autoApprovalCondition()),
                    ProductConfigSource.CONFIGURED);
        } catch (Exception ex) {
            log.warn("[核保配置] 查询产品核保配置异常，按默认配置决策: configSource={}, productCode={}, error={}",
                    ProductConfigSource.UNAVAILABLE, productCode, ex.getMessage());
            return ProductUnderwritingConfig.defaultConfig(ProductConfigSource.UNAVAILABLE);
        }
    }

    /**
     * 解析规则集编码：优先取 dev-505 新增的显式字段 ruleSetCode；
     * 为空时回退解析历史字段 autoApprovalCondition 的 "ruleSet:" 前缀（向后兼容存量配置）。
     *
     * @param ruleSetCode          显式规则集编码（可空）
     * @param autoApprovalCondition 历史自动核保条件描述（可空）
     * @return 规则集编码，均无则返回 null
     */
    private String resolveRuleSetCode(String ruleSetCode, String autoApprovalCondition) {
        if (ruleSetCode != null && !ruleSetCode.isBlank()) {
            return ruleSetCode.trim();
        }
        if (autoApprovalCondition != null && autoApprovalCondition.startsWith(LEGACY_RULE_SET_PREFIX)) {
            String legacy = autoApprovalCondition.substring(LEGACY_RULE_SET_PREFIX.length()).trim();
            if (!legacy.isEmpty()) {
                return legacy;
            }
        }
        return null;
    }
}
