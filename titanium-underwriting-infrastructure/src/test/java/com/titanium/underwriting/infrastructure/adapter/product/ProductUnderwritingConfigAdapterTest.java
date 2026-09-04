package com.titanium.underwriting.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.response.config.UnderwritingConfigResponse;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort.ProductUnderwritingConfig;

class ProductUnderwritingConfigAdapterTest {

    @Test
    void shouldQueryProductConfigurationByCode() {
        ProductApi productApi = mock(ProductApi.class);
        when(productApi.getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a"))
                .thenReturn(ApiResponse.success(new UnderwritingConfigResponse(null, null,
                        new BigDecimal("500000"), List.of(), null, false, false, null)));
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig config = adapter.fetchConfig("TERM_LIFE_V1", "tenant-a");

        assertEquals(false, config.surchargeAcceptable());
        assertEquals(new BigDecimal("500000"), config.manualReviewAmountThreshold());
        assertNull(config.ruleSetCode());
        verify(productApi).getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a");
    }

    @Test
    void shouldResolveRuleSetCodeFromExplicitField() {
        ProductApi productApi = mock(ProductApi.class);
        when(productApi.getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a"))
                .thenReturn(ApiResponse.success(new UnderwritingConfigResponse(null, null,
                        new BigDecimal("500000"), List.of(), null, false, false, "UW_STD_001")));
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig config = adapter.fetchConfig("TERM_LIFE_V1", "tenant-a");

        assertEquals("UW_STD_001", config.ruleSetCode());
    }

    @Test
    void shouldResolveRuleSetCodeFromLegacyAutoApprovalCondition() {
        ProductApi productApi = mock(ProductApi.class);
        // dev-505 向后兼容：历史配置把规则集编码放在 autoApprovalCondition 的 "ruleSet:" 前缀中
        when(productApi.getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a"))
                .thenReturn(ApiResponse.success(new UnderwritingConfigResponse(null, "ruleSet:UW_LEGACY_001",
                        new BigDecimal("500000"), List.of(), null, false, false, null)));
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig config = adapter.fetchConfig("TERM_LIFE_V1", "tenant-a");

        assertEquals("UW_LEGACY_001", config.ruleSetCode());
    }
}
