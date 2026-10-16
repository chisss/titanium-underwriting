package com.titanium.underwriting.application.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.titanium.underwriting.exception.UnderwritingValidationException;
import com.titanium.underwriting.valueobject.UnderwritingParameter;

/**
 * 核保风险参数配置测试（DSN-09 / m25-11）
 * <p>
 * 两段判据：① <b>绑定链路</b>——kebab-case 配置键经 Spring Boot {@link Binder} 落到
 * {@link UnderwritingParameter} record 各分量（「配置了但键名写错」会静默落空，直测绑定
 * 是对该风险的唯一机械判据；单测直接 {@code new} 配置对象会绕过整条绑定链路）；②
 * <b>解析链路</b>——租户命中取覆盖值、未命中/租户为空取 {@link UnderwritingParameter#DEFAULT}。
 * </p>
 */
class UnderwritingParameterPropertiesTest {

    private static final String PREFIX    = "titanium.underwriting.risk-parameter";
    private static final String TENANT_ID = "TENANT-001";

    // ── ① 绑定链路 ────────────────────────────────────────────────────────

    @Test
    void bindsFullTenantOverrideFromKebabCaseConfigurationKeys() {
        Map<String, String> source = new HashMap<>();
        String prefix = PREFIX + ".tenant-parameters." + TENANT_ID + ".";
        source.put(prefix + "medical-history-score", "25");
        source.put(prefix + "family-history-score", "12");
        source.put(prefix + "smoking-score", "18");
        source.put(prefix + "bmi-lower-bound", "18.0");
        source.put(prefix + "bmi-overweight-bound", "26");
        source.put(prefix + "bmi-obesity-bound", "30");
        source.put(prefix + "bmi-deviation-score", "8");
        source.put(prefix + "score-cap", "120");
        source.put(prefix + "sub-standard-threshold", "28");
        source.put(prefix + "high-risk-threshold", "55");
        source.put(prefix + "uninsurable-threshold", "90");

        UnderwritingParameter bound = bind(source).resolve(TENANT_ID);

        assertEquals(25, bound.medicalHistoryScore(), "medical-history-score 未按 kebab-case 绑定");
        assertEquals(12, bound.familyHistoryScore(), "family-history-score 未按 kebab-case 绑定");
        assertEquals(18, bound.smokingScore(), "smoking-score 未按 kebab-case 绑定");
        assertDecimal("18.0", bound.bmiLowerBound(), "bmi-lower-bound 未按 kebab-case 绑定");
        assertDecimal("26", bound.bmiOverweightBound(), "bmi-overweight-bound 未按 kebab-case 绑定");
        assertDecimal("30", bound.bmiObesityBound(), "bmi-obesity-bound 未按 kebab-case 绑定");
        assertEquals(8, bound.bmiDeviationScore(), "bmi-deviation-score 未按 kebab-case 绑定");
        assertEquals(120, bound.scoreCap(), "score-cap 未按 kebab-case 绑定");
        assertEquals(28, bound.subStandardThreshold(), "sub-standard-threshold 未按 kebab-case 绑定");
        assertEquals(55, bound.highRiskThreshold(), "high-risk-threshold 未按 kebab-case 绑定");
        assertEquals(90, bound.uninsurableThreshold(), "uninsurable-threshold 未按 kebab-case 绑定");
    }

    @Test
    void partialTenantOverrideFailsBindingWithDomainValidation() {
        // 仅给 2 项 ⇒ 其余分量落默认（int 为 0 / BigDecimal 为 null），被 record 紧凑构造器拒绝。
        // 锁定「写了一半的租户覆盖必须构建期失败」——否则缺失项会以 0/null 语义静默生效。
        Map<String, String> source = new HashMap<>();
        String prefix = PREFIX + ".tenant-parameters." + TENANT_ID + ".";
        source.put(prefix + "sub-standard-threshold", "25");
        source.put(prefix + "high-risk-threshold", "55");

        Exception thrown = assertThrows(Exception.class, () -> bind(source),
                "部分字段的租户覆盖须拒绝绑定（缺失分量的 0/null 语义不是合法参数组合）");
        assertTrue(causeChainContains(thrown, UnderwritingValidationException.class),
                "绑定失败根因须为域校验异常（未见即说明缺失分量静默生效）: " + thrown);
    }

    // ── ② 解析链路 ────────────────────────────────────────────────────────

    @Test
    void resolveReturnsDefaultForUncoveredTenantAndNullTenant() {
        UnderwritingParameterProperties props = new UnderwritingParameterProperties();

        assertSame(UnderwritingParameter.DEFAULT, props.resolve(TENANT_ID),
                "无任何覆盖配置时须取域默认参数集");
        assertSame(UnderwritingParameter.DEFAULT, props.resolve(null),
                "租户为空时须取域默认参数集（不得抛错）");
    }

    @Test
    void resolveReturnsTenantOverrideWhenConfiguredAndDefaultForOthers() {
        UnderwritingParameterProperties props = new UnderwritingParameterProperties();
        UnderwritingParameter override = thresholdParameter(25, 55, 85);
        props.setTenantParameters(Map.of(TENANT_ID, override));

        assertSame(override, props.resolve(TENANT_ID), "命中租户覆盖须返回该租户参数集");
        assertSame(UnderwritingParameter.DEFAULT, props.resolve("TENANT-002"),
                "未命中租户须回退默认参数集，不得串用其他租户覆盖");
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    private static UnderwritingParameterProperties bind(Map<String, String> source) {
        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bind(PREFIX, Bindable.of(UnderwritingParameterProperties.class)).get();
    }

    private static boolean causeChainContains(Throwable thrown, Class<? extends Throwable> type) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static UnderwritingParameter thresholdParameter(int subStandardThreshold, int highRiskThreshold,
            int uninsurableThreshold) {
        return new UnderwritingParameter(20, 10, 15, new BigDecimal("18.5"), new BigDecimal("28"),
                new BigDecimal("32"), 10, 100, subStandardThreshold, highRiskThreshold, uninsurableThreshold);
    }

    private static void assertDecimal(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                message + "：expected=" + expected + ", actual=" + actual);
    }
}
