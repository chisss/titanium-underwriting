package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 体检报告值对象
 * <p>
 * 高保额寿险/重疾核保输入，对应 {@code UnderwritingInputType.PHYSICAL_EXAM}。 采集
 * BMI、血压、血糖及异常项清单，值对象内聚体检风险评分逻辑（充血模型）。
 * </p>
 *
 * @param bmi 身体质量指数 BMI
 * @param systolicPressure 收缩压（高压，mmHg）
 * @param diastolicPressure 舒张压（低压，mmHg）
 * @param bloodGlucose 空腹血糖（mmol/L）
 * @param abnormalItems 体检异常项清单，无异常则为空列表
 * @author wei.sun
 * @since 2026/6/23
 */
public record PhysicalExamResult(BigDecimal bmi, BigDecimal systolicPressure, BigDecimal diastolicPressure,
                                 BigDecimal bloodGlucose, List<String> abnormalItems)
        implements
            Serializable {

    /**
     * 值对象名（用于校验异常上下文）
     */
    private static final String VO_NAME = "PhysicalExamResult";

    public PhysicalExamResult(BigDecimal bmi, BigDecimal systolicPressure, BigDecimal diastolicPressure,
                              BigDecimal bloodGlucose, List<String> abnormalItems) {
        if (bmi == null || bmi.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.BMI_POSITIVE, VO_NAME, "bmi");
        }
        if (systolicPressure == null || systolicPressure.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.SYSTOLIC_PRESSURE_POSITIVE, VO_NAME, "systolicPressure");
        }
        if (diastolicPressure == null || diastolicPressure.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.DIASTOLIC_PRESSURE_POSITIVE, VO_NAME, "diastolicPressure");
        }
        if (bloodGlucose == null || bloodGlucose.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.BLOOD_GLUCOSE_NEGATIVE, VO_NAME, "bloodGlucose");
        }
        this.bmi = bmi;
        this.systolicPressure = systolicPressure;
        this.diastolicPressure = diastolicPressure;
        this.bloodGlucose = bloodGlucose;
        this.abnormalItems = abnormalItems == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(abnormalItems));
    }

    /**
     * 体检风险评分（0-100，越高风险越大）
     * <p>
     * 综合 BMI、血压、血糖偏离正常区间及异常项数量加权评分（充血模型）。
     * </p>
     *
     * @return 体检风险评分
     */
    public int riskScore() {
        int score = 0;
        // BMI 超重/肥胖
        if (bmi.compareTo(BigDecimal.valueOf(28)) >= 0) {
            score += 15;
        }
        // 高血压：收缩压>=140 或 舒张压>=90
        if (systolicPressure.compareTo(BigDecimal.valueOf(140)) >= 0
                || diastolicPressure.compareTo(BigDecimal.valueOf(90)) >= 0) {
            score += 20;
        }
        // 高血糖：空腹>=7.0 mmol/L（糖尿病参考线）
        if (bloodGlucose.compareTo(BigDecimal.valueOf(7.0)) >= 0) {
            score += 20;
        }
        // 异常项：每项 +15
        score += abnormalItems.size() * 15;
        return Math.min(score, 100);
    }
}
