package com.titanium.underwriting.port.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.port.product.ProductUnderwritingConfigPort.ProductUnderwritingConfig;

/**
 * 产品核保配置快照测试（m11-1404）
 * <p>
 * 锁死两件事：① <b>来源可区分</b>——兜底配置对象永远非 null，故「本次决策是否有产品策略依据」只能经
 * {@link ProductUnderwritingConfig#configured()} 判定，做存在性判断必然为真、语义盲区复现；
 * ② <b>显式化语义不得改变判定</b>——三态下 {@code surchargeAcceptable} 沿用存量兼容取值 true，
 * 改值会使存量「加费承保」翻转为「拒保」，属破坏性变更。
 * </p>
 */
class ProductUnderwritingConfigTest {

    @Test
    @DisplayName("来源缺失即拒绝构造：从构造期堵死「来源不明」的再次塌缩")
    void shouldRejectNullConfigSource() {
        assertThrows(NullPointerException.class,
                () -> new ProductUnderwritingConfig(true, null, null, null),
                "来源不明的配置快照不得被构造——否则「没配」与「取不到」再次不可区分");
    }

    @Test
    @DisplayName("仅 CONFIGURED 视作有产品策略依据，两种兜底处境均否")
    void onlyConfiguredSourceCountsAsConfigured() {
        ProductUnderwritingConfig configured = new ProductUnderwritingConfig(true, null, "UW_STD_001",
                ProductConfigSource.CONFIGURED);
        assertTrue(configured.configured());

        assertFalse(ProductUnderwritingConfig.defaultConfig(ProductConfigSource.NOT_CONFIGURED).configured(),
                "产品没配核保策略时，取值来自兜底，不得被当作有依据");
        assertFalse(ProductUnderwritingConfig.defaultConfig(ProductConfigSource.UNAVAILABLE).configured(),
                "产品域取不到配置时，取值同样来自兜底，且不得与「没配」混同");
    }

    @Test
    @DisplayName("兜底取值不变：仍允许加费、不限金额、未接规则引擎（存量判定回归保护）")
    void defaultConfigKeepsLegacyValues() {
        for (ProductConfigSource source : List.of(ProductConfigSource.NOT_CONFIGURED,
                ProductConfigSource.UNAVAILABLE)) {
            ProductUnderwritingConfig config = ProductUnderwritingConfig.defaultConfig(source);

            assertTrue(config.surchargeAcceptable(), "存量兼容取值不得因来源显式化而改变（m11-1404 明确不改判定）");
            assertNull(config.manualReviewAmountThreshold());
            assertNull(config.ruleSetCode());
            assertFalse(config.ruleEngineEnabled());
            assertEquals(source, config.configSource(), "兜底须保留各自处境，不得回落为统一来源");
        }
    }

    @Test
    @DisplayName("规则集编码判定：null 与纯空白均视为未接入规则引擎")
    void ruleEngineEnabledRejectsBlankCode() {
        assertFalse(configured(null).ruleEngineEnabled());
        assertFalse(configured("   ").ruleEngineEnabled());
        assertTrue(configured("UW_STD_001").ruleEngineEnabled());
    }

    @Test
    @DisplayName("来源枚举：configured 语义、code 反查与未知码兜底")
    void enumSourceSemantics() {
        assertTrue(ProductConfigSource.CONFIGURED.configured());
        assertFalse(ProductConfigSource.NOT_CONFIGURED.configured());
        assertFalse(ProductConfigSource.UNAVAILABLE.configured());

        assertEquals(ProductConfigSource.NOT_CONFIGURED, ProductConfigSource.fromCode("NOT_CONFIGURED"));
        assertEquals(ProductConfigSource.UNAVAILABLE, ProductConfigSource.fromCode("UNAVAILABLE"));
        assertNull(ProductConfigSource.fromCode("UNKNOWN"), "未知码返回 null，由调用方显式处理");
        assertNull(ProductConfigSource.fromCode(null));

        assertEquals(ProductConfigSource.NOT_CONFIGURED, ProductConfigSource.valueOf("NOT_CONFIGURED"),
                "枚举名即跨域/持久化契约，不得改名");
    }

    private ProductUnderwritingConfig configured(String ruleSetCode) {
        return new ProductUnderwritingConfig(true, null, ruleSetCode, ProductConfigSource.CONFIGURED);
    }
}
