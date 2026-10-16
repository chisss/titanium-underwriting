package com.titanium.underwriting.valueobject;

import java.io.Serializable;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import lombok.Builder;

/**
 * 核保输入容器值对象
 * <p>
 * 聚合四类险种专属核保输入，按实际填充的输入综合评估整体风险等级。 不同险种填充不同输入：寿险/重疾填健康告知+体检；意外险填职业；车险填车辆风险。
 * 容器内聚整体风险等级判定逻辑（充血模型），替代聚合根中"金额硬编码"的简单规则。
 * </p>
 * <p>
 * 评分与等级判定的数值已参数化（{@link UnderwritingParameter}，DSN-09 / m25-11，支持租户级覆盖）；
 * 产品接入规则引擎时（ruleSetCode 非空）由核保决策编排器改走规则集路径，本容器的内置评分
 * 作为未接入规则集的基线路径保留。
 * </p>
 *
 * @param healthDeclaration 健康告知（寿险/重疾/医疗）
 * @param physicalExamResult 体检报告（高保额寿险/重疾）
 * @param occupationInfo 职业信息（意外险/定期寿险）
 * @param vehicleRiskInfo 车辆风险信息（车险）
 * @param financialAssessment 财务评估（高保额寿险财务核保）
 * @param insuredRiskFactors 被保人粗粒度风险要素（年龄/性别/职业类别/BMI；承保前置链路自动透传）
 * @author wei.sun
 * @since 2026/6/23
 */
@Builder
public record UnderwritingInput(HealthDeclaration healthDeclaration, PhysicalExamResult physicalExamResult,
                                OccupationInfo occupationInfo,
                                VehicleRiskInfo vehicleRiskInfo,
                                FinancialAssessment financialAssessment,
                                InsuredRiskFactors insuredRiskFactors)
        implements
            Serializable {

    /**
     * 是否已提供任一险种专属输入
     *
     * @return true 表示至少存在一项核保输入
     */
    public boolean hasAnyInput() {
        return healthDeclaration != null || physicalExamResult != null || occupationInfo != null
                || vehicleRiskInfo != null || financialAssessment != null
                || (insuredRiskFactors != null && insuredRiskFactors.hasAny());
    }

    /**
     * 综合风险评分（取各项输入评分的最大值，0-100）
     * <p>
     * 采用"短板优先"策略：任一维度高风险即拉高整体风险，符合保险核保保守原则（充血模型）。
     * 取最大值而非求和，故同一要素同时出现在粗粒度容器（{@link #insuredRiskFactors}）与明细块
     * （职业/体检）时不会重复计分。健康告知块的可配置权重经 {@code parameter} 下传
     * （DSN-09 / m25-11），其余输入块评分为域内固定规则（不下传参数）。
     * </p>
     *
     * @param parameter 核保风险参数（不可为 null；读侧预览等未持有租户参数场景显式传
     *                  {@link UnderwritingParameter#DEFAULT}）
     * @return 综合风险评分
     */
    public int aggregateRiskScore(UnderwritingParameter parameter) {
        int score = 0;
        if (healthDeclaration != null) {
            score = Math.max(score, healthDeclaration.riskScore(parameter));
        }
        if (physicalExamResult != null) {
            score = Math.max(score, physicalExamResult.riskScore());
        }
        if (occupationInfo != null) {
            score = Math.max(score, occupationInfo.riskScore());
        }
        if (vehicleRiskInfo != null) {
            score = Math.max(score, vehicleRiskInfo.riskScore());
        }
        if (financialAssessment != null) {
            score = Math.max(score, financialAssessment.riskScore());
        }
        if (insuredRiskFactors != null) {
            score = Math.max(score, insuredRiskFactors.riskScore());
        }
        return score;
    }

    /**
     * 是否需人工财务核保（高保额寿险保额显著超出财务合理范围）。
     *
     * @return 提供了财务评估且触发人工财务核保阈值时返回 {@code true}
     */
    public boolean requiresManualFinancialReview() {
        return financialAssessment != null && financialAssessment.requiresManualFinancialReview();
    }

    /**
     * 评估整体风险等级（充血模型）
     * <p>
     * 等级阈值取自 {@link UnderwritingParameter}（DSN-09 / m25-11，支持租户级覆盖）。
     * </p>
     *
     * @param parameter 核保风险参数（不可为 null；调用方未持有租户参数时应显式传
     *                  {@link UnderwritingParameter#DEFAULT}，禁止再新增无参重载）
     * @return 风险等级；无任何输入时返回标准体
     */
    public UnderwritingEnum.RiskLevel assessRiskLevel(UnderwritingParameter parameter) {
        int score = aggregateRiskScore(parameter);
        if (score >= parameter.uninsurableThreshold()) {
            return UnderwritingEnum.RiskLevel.UNINSURABLE;
        }
        if (score >= parameter.highRiskThreshold()) {
            return UnderwritingEnum.RiskLevel.HIGH_RISK;
        }
        if (score >= parameter.subStandardThreshold()) {
            return UnderwritingEnum.RiskLevel.SUB_STANDARD;
        }
        return UnderwritingEnum.RiskLevel.STANDARD;
    }
}
