package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;

import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 财务评估值对象
 * <p>
 * 高保额寿险核保的财务核保输入，对应财务核保维度。采集被保人/投保人年收入、净资产与
 * 申请保额，用于评估「保额是否与财务承受能力匹配」——高保额寿险须防止逆选择与道德风险，
 * 保额显著超出财务合理范围时应触发人工财务核保。值对象内聚保额倍数与财务风险评分（充血模型）。
 * </p>
 *
 * @param annualIncome 年收入
 * @param netWorth 净资产
 * @param requestedSumInsured 申请保额
 * @param incomeSource 收入来源说明（工资/经营/投资等）
 * @author wei.sun
 * @since 2026/7/11
 */
public record FinancialAssessment(BigDecimal annualIncome, BigDecimal netWorth, BigDecimal requestedSumInsured,
                                  String incomeSource)
        implements
            Serializable {

    /** 值对象名（用于校验异常上下文） */
    private static final String VO_NAME = "FinancialAssessment";

    /** 保额/年收入倍数警戒线：寿险保额一般不超过年收入的 10 倍 */
    private static final BigDecimal INCOME_MULTIPLE_WARN = BigDecimal.valueOf(10);

    /** 保额/年收入倍数高风险线：超过 20 倍视为显著超额 */
    private static final BigDecimal INCOME_MULTIPLE_HIGH = BigDecimal.valueOf(20);

    public FinancialAssessment {
        if (annualIncome == null || annualIncome.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnderwritingValidationException(VO_NAME, "annualIncome", "年收入不能为负");
        }
        if (netWorth == null) {
            throw new UnderwritingValidationException(VO_NAME, "netWorth", "净资产不能为空");
        }
        if (requestedSumInsured == null || requestedSumInsured.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(VO_NAME, "requestedSumInsured", "申请保额必须大于零");
        }
    }

    /**
     * 保额相对年收入的倍数（年收入为零时返回一个极大值以判为高风险）。
     *
     * @return 保额/年收入倍数
     */
    public BigDecimal sumInsuredToIncomeMultiple() {
        if (annualIncome.compareTo(BigDecimal.ZERO) == 0) {
            return INCOME_MULTIPLE_HIGH.add(BigDecimal.ONE);
        }
        return requestedSumInsured.divide(annualIncome, 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 财务风险评分（0-100，越高风险越大）。
     * <p>
     * 依据保额/年收入倍数与净资产覆盖度综合评分：倍数超警戒线加分、超高风险线大幅加分；
     * 申请保额超过净资产再加分。用于将财务维度并入整体核保风险评估（充血模型）。
     * </p>
     *
     * @return 财务风险评分
     */
    public int riskScore() {
        int score = 0;
        BigDecimal multiple = sumInsuredToIncomeMultiple();
        if (multiple.compareTo(INCOME_MULTIPLE_HIGH) >= 0) {
            score += 60;
        } else if (multiple.compareTo(INCOME_MULTIPLE_WARN) >= 0) {
            score += 30;
        }
        // 申请保额超过净资产：财务覆盖不足，+20
        if (requestedSumInsured.compareTo(netWorth) > 0) {
            score += 20;
        }
        return Math.min(score, 100);
    }

    /**
     * 财务状况是否需人工财务核保（保额显著超出财务合理范围）。
     *
     * @return 需人工财务核保返回 {@code true}
     */
    public boolean requiresManualFinancialReview() {
        return sumInsuredToIncomeMultiple().compareTo(INCOME_MULTIPLE_HIGH) >= 0;
    }
}
