package com.titanium.underwriting.port.ruleengine;

import java.util.Map;

import com.titanium.underwriting.valueobject.RuleExecutionResult;

/**
 * 规则引擎执行出口端口（driven port，与聚合平级，按对端域 ruleengine 拆子包）
 * <p>
 * 核保域把「产品核保配置 → 规则集执行」的出口能力声明为本端口：给定规则集编码与变量上下文，
 * 由规则引擎域执行并返回决策结论（dev-505 链路）。领域层不依赖规则引擎域任何类型——
 * 决策经 {@link RuleExecutionResult} 值对象承载，枚举细节由 infrastructure 适配器转换。
 * </p>
 */
public interface RuleEngineServicePort {

    /**
     * 执行规则集（首个命中即生效）。
     *
     * @param tenantId    租户ID
     * @param ruleSetCode 规则集编码（产品核保配置的 ruleSetCode）
     * @param context     规则变量上下文（核保输入 + 特征中心提取的特征值）
     * @return 规则执行结果（决策结论、原因与动作参数）
     */
    RuleExecutionResult executeRuleSet(String tenantId, String ruleSetCode, Map<String, Object> context);
}
