package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;

import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 职业信息值对象
 * <p>
 * 意外险/定期寿险核保输入，对应 {@code UnderwritingInputType.OCCUPATION}。 职业类别 1-6
 * 类（数字越大风险越高），配合危险系数用于意外险风险定价。 值对象内聚职业风险评分逻辑（充血模型）。
 * </p>
 *
 * @param occupationName 职业名称
 * @param occupationCategory 职业类别（1-6 类，类别越高风险越大）
 * @param riskFactor 危险系数（>=1.0，用于费率加成）
 * @author wei.sun
 * @since 2026/6/23
 */
public record OccupationInfo(String occupationName, int occupationCategory,
                             BigDecimal riskFactor)
        implements
            Serializable {

    /**
     * 值对象名（用于校验异常上下文）
     */
    private static final String VO_NAME = "OccupationInfo";

    /**
     * 最低职业类别
     */
    private static final int MIN_CATEGORY = 1;

    /**
     * 最高职业类别（拒保类）
     */
    private static final int MAX_CATEGORY = 6;

    public OccupationInfo {
        if (occupationName == null || occupationName.trim().isEmpty()) {
            throw new UnderwritingValidationException(VO_NAME, "occupationName", "职业名称不能为空");
        }
        if (occupationCategory < MIN_CATEGORY || occupationCategory > MAX_CATEGORY) {
            throw new UnderwritingValidationException(VO_NAME, "occupationCategory", "职业类别必须在 1-6 之间");
        }
        if (riskFactor == null || riskFactor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(VO_NAME, "riskFactor", "危险系数必须大于零");
        }
    }

    /**
     * 职业风险评分（0-100，越高风险越大）
     * <p>
     * 以职业类别为主因子（每级 +15），叠加危险系数偏离基准（1.0）的影响（充血模型）。
     * </p>
     *
     * @return 职业风险评分
     */
    public int riskScore() {
        // 职业类别 1-6 → 0/15/30/45/60/75
        int categoryScore = (occupationCategory - 1) * 15;
        // 危险系数高于 1.5 额外 +15
        int factorScore = riskFactor.compareTo(BigDecimal.valueOf(1.5)) >= 0 ? 15 : 0;
        return Math.min(categoryScore + factorScore, 100);
    }
}
