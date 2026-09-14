package com.titanium.underwriting.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.response.config.UnderwritingConfigResponse;
import com.titanium.underwriting.common.enums.ProductConfigSource;
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

    @Test
    @DisplayName("成功路径：来源标记为 CONFIGURED，取值可信")
    void shouldMarkSourceAsConfiguredOnSuccess() {
        ProductApi productApi = mock(ProductApi.class);
        when(productApi.getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a"))
                .thenReturn(ApiResponse.success(new UnderwritingConfigResponse(null, null,
                        new BigDecimal("500000"), List.of(), null, true, false, "UW_STD_001")));
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig config = adapter.fetchConfig("TERM_LIFE_V1", "tenant-a");

        assertEquals(ProductConfigSource.CONFIGURED, config.configSource());
        assertTrue(config.configured());
    }

    @Test
    @DisplayName("未提供产品编码：来源标记为 NOT_CONFIGURED（「没配」），且不发起远程调用")
    void shouldMarkNotConfiguredWhenProductCodeMissing() {
        ProductApi productApi = mock(ProductApi.class);
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig nullCode = adapter.fetchConfig(null, "tenant-a");
        ProductUnderwritingConfig blankCode = adapter.fetchConfig("   ", "tenant-a");

        assertEquals(ProductConfigSource.NOT_CONFIGURED, nullCode.configSource());
        assertEquals(ProductConfigSource.NOT_CONFIGURED, blankCode.configSource());
        assertFalse(nullCode.configured());
        assertTrue(nullCode.surchargeAcceptable(), "兜底取值不得因来源显式化而改变（m11-1404 明确不改判定）");
        verifyNoInteractions(productApi);
    }

    @Test
    @DisplayName("产品域返回不可用：来源标记为 UNAVAILABLE（「取不到」），与「没配」可区分")
    void shouldMarkUnavailableWhenProductReturnsFailure() {
        ProductApi productApi = mock(ProductApi.class);
        when(productApi.getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a"))
                .thenReturn(ApiResponse.error(SystemErrorCode.RESOURCE_NOT_FOUND));
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig config = adapter.fetchConfig("TERM_LIFE_V1", "tenant-a");

        assertEquals(ProductConfigSource.UNAVAILABLE, config.configSource());
        assertFalse(config.configured());
        assertTrue(config.surchargeAcceptable(), "兜底取值不得因来源显式化而改变");
    }

    @Test
    @DisplayName("产品域调用异常：来源标记为 UNAVAILABLE，核保流程不被阻断")
    void shouldMarkUnavailableWhenProductCallThrows() {
        ProductApi productApi = mock(ProductApi.class);
        when(productApi.getUnderwritingConfigByCode("TERM_LIFE_V1", "tenant-a"))
                .thenThrow(new IllegalStateException("product service down"));
        ProductUnderwritingConfigAdapter adapter = new ProductUnderwritingConfigAdapter(productApi);

        ProductUnderwritingConfig config = adapter.fetchConfig("TERM_LIFE_V1", "tenant-a");

        assertEquals(ProductConfigSource.UNAVAILABLE, config.configSource());
        assertFalse(config.configured());
        assertTrue(config.surchargeAcceptable());
    }
}
