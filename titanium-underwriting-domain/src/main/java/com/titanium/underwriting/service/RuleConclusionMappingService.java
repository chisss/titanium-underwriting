package com.titanium.underwriting.service;

import com.titanium.underwriting.valueobject.RuleExecutionResult;
import com.titanium.underwriting.valueobject.RuleUnderwritingDecision;

/**
 * 规则引擎结论映射领域服务（dev-505 纯领域规则）
 * <p>
 * 把规则引擎执行结果 {@link RuleExecutionResult} 映射为核保域决策 {@link RuleUnderwritingDecision}：
 * 结论 PASS 回退聚合内置评分路径（返回 null）；REFER 转人工复核；SURCHARGE 加费承保；
 * REJECT 依「加费率 + 产品是否允许加费」判定加费承保或拒保；未知结论抛结构化业务错误。
 * </p>
 * <p>
 * 本服务为「三无」纯领域服务：无 CommandGateway、无外部 Port、无基础设施依赖，
 * 入参出参仅值对象，可脱离容器 {@code new} 直测（见 {@code RuleConclusionMappingServiceTest}）。
 * </p>
 */
public interface RuleConclusionMappingService {

    /**
     * 规则引擎执行结果 → 核保决策。
     *
     * @param result              规则引擎执行结果
     * @param surchargeAcceptable 产品核保配置是否允许加费承保（false 时 REJECT 带加费率也拒保）
     * @return 核保决策；PASS 结论返回 null 表示回退聚合内置评分路径
     */
    RuleUnderwritingDecision map(RuleExecutionResult result, boolean surchargeAcceptable);
}
