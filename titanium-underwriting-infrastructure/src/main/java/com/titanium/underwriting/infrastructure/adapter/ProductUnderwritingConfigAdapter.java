package com.titanium.underwriting.infrastructure.adapter;


import org.springframework.stereotype.Component;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.response.UnderwritingConfigResponse;
import com.titanium.underwriting.port.ProductUnderwritingConfigPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 产品核保配置适配器（driven adapter，位于 infrastructure）
 * <p>
 * 实现 {@link ProductUnderwritingConfigPort}，经 Feign {@link ProductApi} 调用产品域
 * {@code /{productId}/underwriting-config} 端点，将弱类型 JSON 配置解析为
 * {@link ProductUnderwritingConfig} 值对象返回给 application 层。
 * 产品域不可用时兜底返回默认配置（允许加费，不限金额），不阻断核保流程。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductUnderwritingConfigAdapter implements ProductUnderwritingConfigPort {

    private final ProductApi productApi;

    @Override
    public ProductUnderwritingConfig fetchConfig(String productCode, String tenantId) {
        if (productCode == null || productCode.isBlank()) {
            return ProductUnderwritingConfig.defaultConfig();
        }
        try {
            ApiResponse<UnderwritingConfigResponse> response = productApi.getUnderwritingConfigByCode(productCode, tenantId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("[核保配置] 产品核保配置不可用，使用默认配置: productCode={}", productCode);
                return ProductUnderwritingConfig.defaultConfig();
            }
            // 契约已为强类型 record，直接取访问器，禁止 JSON 往返弱类型解析（禁 Map/JSONObject 接契约）
            UnderwritingConfigResponse config = response.getData();
            return new ProductUnderwritingConfig(config.surchargeAcceptable(), config.manualReviewAmountThreshold());
        } catch (Exception ex) {
            log.warn("[核保配置] 查询产品核保配置异常，使用默认配置: productCode={}, error={}", productCode, ex.getMessage());
            return ProductUnderwritingConfig.defaultConfig();
        }
    }
}
