package com.titanium.underwriting.valueobject;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

/**
 * 规则引擎核保决策值对象（dev-505 规则链路结论，随命令进入聚合根）
 * <p>
 * 由领域服务 {@code RuleConclusionMappingService} 将 {@link RuleExecutionResult} 映射为本决策，
 * 经 {@code DecideUnderwritingCommand.ruleDecision} 传入聚合根：聚合根据此直接落盘结论与状态，
 * 不再走内置评分路径。转人工复核结论不产出风险等级与核保结论（{@code riskLevel}/{@code conclusionType}
 * 为 null，仅状态为 {@code MANUAL_REVIEW}）。
 * </p>
 *
 * @param conclusionType 核保结论（转人工复核时为 null）
 * @param riskLevel      风险等级（转人工复核时为 null）
 * @param newStatus      核保状态（STANDARD/RATED/MANUAL_REVIEW/DECLINED，由结论映射确定）
 * @param extraPremium   加费明细（加费承保时产出，其余为 null）
 * @param reason         决策原因（规则引擎给出的原因说明，随事件落库供展示/审计）
 */
public record RuleUnderwritingDecision(UnderwritingEnum.ConclusionType conclusionType,
                                       UnderwritingEnum.RiskLevel riskLevel,
                                       UnderwritingEnum.UnderwritingStatus newStatus,
                                       ExtraPremium extraPremium,
                                       String reason) {
}
