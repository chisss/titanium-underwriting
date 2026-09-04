package com.titanium.underwriting.valueobject;

import java.util.Map;

import com.titanium.underwriting.common.constant.UnderwritingConstants;

/**
 * 规则引擎执行结果值对象（dev-505 规则链路的域内结论载体）
 * <p>
 * 屏蔽规则引擎域枚举（{@code RuleDecision}）细节：适配器把决策结论转为本值对象，
 * 领域结论映射服务据此推导最终核保结论（通过/转人工/加费/拒保）。
 * </p>
 *
 * @param passed       是否放行（PASS 与 SURCHARGE 均视为放行，加费语义由结论映射区分）
 * @param conclusion   规则引擎决策结论原文（PASS/REJECT/REFER/SURCHARGE，供映射与审计留痕）
 * @param reason       规则引擎给出的原因说明（可空）
 * @param actionParams 规则命中动作参数（如 surchargeRate 加费率、reason 原因），可空
 */
public record RuleExecutionResult(boolean passed, String conclusion, String reason,
                                  Map<String, Object> actionParams) {

    /**
     * 便捷判定：结论是否为加费承保（SURCHARGE）。
     *
     * @return true 表示规则引擎要求加费后承保
     */
    public boolean surcharge() {
        return UnderwritingConstants.RULE_CONCLUSION_SURCHARGE.equals(conclusion);
    }

    /**
     * 便捷判定：结论是否为转人工（REFER）。
     *
     * @return true 表示规则引擎要求转人工复核
     */
    public boolean refer() {
        return UnderwritingConstants.RULE_CONCLUSION_REFER.equals(conclusion);
    }

    /**
     * 便捷判定：结论是否为拒绝（REJECT）。
     *
     * @return true 表示规则引擎给出拒绝结论
     */
    public boolean reject() {
        return UnderwritingConstants.RULE_CONCLUSION_REJECT.equals(conclusion);
    }
}
