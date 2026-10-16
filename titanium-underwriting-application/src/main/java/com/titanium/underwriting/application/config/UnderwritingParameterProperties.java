package com.titanium.underwriting.application.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.titanium.underwriting.valueobject.UnderwritingParameter;

import lombok.Getter;
import lombok.Setter;

/**
 * 核保风险参数配置（DSN-09 / m25-11）
 * <p>
 * 外部化「内置加权评分」路径的风险参数（{@link UnderwritingParameter}），支持租户级覆盖：
 * 未命中覆盖的租户一律取 {@link UnderwritingParameter#DEFAULT}（等价于改造前的硬编码行为）。
 * 配置示例（application.yml，键为 kebab-case）：
 * </p>
 * <pre>{@code
 * titanium:
 *   underwriting:
 *     risk-parameter:
 *       tenant-parameters:
 *         TENANT-001:
 *           sub-standard-threshold: 25
 *           high-risk-threshold: 55
 * }</pre>
 * <p>
 * 🔴 租户键即租户 ID 原样；若租户 ID 含 `.`，须用 Spring 的 {@code [key]} 转义形态书写
 * （如 {@code tenant-parameters[TENANT.A]}:），否则会被解析为嵌套路径。
 * </p>
 * <p>
 * 装配：{@code @Component} 由启动类组件扫描（{@code com.titanium.underwriting}）装载；
 * application 层编排器（路由/决策）经 {@link #resolve(String)} 解析后充实进核保命令。
 * </p>
 *
 * @author wei.sun
 * @since 2026/10/15
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "titanium.underwriting.risk-parameter")
public class UnderwritingParameterProperties {

    /**
     * 租户级参数覆盖：键为租户 ID，值为该租户的完整参数集（未列出的字段取 record 默认构造语义，
     * 即必须显式给出全部 11 项；如需仅覆盖个别项，可在租户值内完整书写，构建期绑定校验会拒绝非法组合）。
     */
    private Map<String, UnderwritingParameter> tenantParameters = new HashMap<>();

    /**
     * 解析指定租户的核保风险参数：命中租户覆盖取覆盖值，否则取域默认参数集。
     *
     * @param tenantId 租户 ID（可为 null——null 时一律取默认参数集）
     * @return 该租户生效的核保风险参数（恒非 null）
     */
    public UnderwritingParameter resolve(String tenantId) {
        if (tenantId == null) {
            return UnderwritingParameter.DEFAULT;
        }
        return tenantParameters.getOrDefault(tenantId, UnderwritingParameter.DEFAULT);
    }
}
