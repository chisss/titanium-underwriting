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
 * TODO 规则引擎接入：当前为内置加权评分规则，后续应下沉至 titanium-rule-engine 模块按租户/险种配置。
 * </p>
 *
 * @param healthDeclaration 健康告知（寿险/重疾/医疗）
 * @param physicalExamResult 体检报告（高保额寿险/重疾）
 * @param occupationInfo 职业信息（意外险/定期寿险）
 * @param vehicleRiskInfo 车辆风险信息（车险）
 * @author wei.sun
 * @since 2026/6/23
 */
@Builder
public record UnderwritingInput(HealthDeclaration healthDeclaration, PhysicalExamResult physicalExamResult,
                                OccupationInfo occupationInfo,
                                VehicleRiskInfo vehicleRiskInfo)
        implements
            Serializable {

    /**
     * 次标准体风险阈值（评分 >= 该值判为次标准体）
     */
    private static final int THRESHOLD_SUB_STANDARD = 30;

    /**
     * 高风险体风险阈值
     */
    private static final int THRESHOLD_HIGH_RISK = 60;

    /**
     * 不可保体风险阈值
     */
    private static final int THRESHOLD_UNINSURABLE = 85;

    /**
     * 是否已提供任一险种专属输入
     *
     * @return true 表示至少存在一项核保输入
     */
    public boolean hasAnyInput() {
        return healthDeclaration != null || physicalExamResult != null || occupationInfo != null
                || vehicleRiskInfo != null;
    }

    /**
     * 综合风险评分（取各项输入评分的最大值，0-100）
     * <p>
     * 采用"短板优先"策略：任一维度高风险即拉高整体风险，符合保险核保保守原则（充血模型）。
     * </p>
     *
     * @return 综合风险评分
     */
    public int aggregateRiskScore() {
        int score = 0;
        if (healthDeclaration != null) {
            score = Math.max(score, healthDeclaration.riskScore());
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
        return score;
    }

    /**
     * 评估整体风险等级（充血模型）
     *
     * @return 风险等级；无任何输入时返回标准体
     */
    public UnderwritingEnum.RiskLevel assessRiskLevel() {
        int score = aggregateRiskScore();
        if (score >= THRESHOLD_UNINSURABLE) {
            return UnderwritingEnum.RiskLevel.UNINSURABLE;
        }
        if (score >= THRESHOLD_HIGH_RISK) {
            return UnderwritingEnum.RiskLevel.HIGH_RISK;
        }
        if (score >= THRESHOLD_SUB_STANDARD) {
            return UnderwritingEnum.RiskLevel.SUB_STANDARD;
        }
        return UnderwritingEnum.RiskLevel.STANDARD;
    }
}
