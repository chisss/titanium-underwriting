package com.titanium.underwriting.domain.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.titanium.underwriting.domain.exception.UnderwritingValidationException;

/**
 * 健康告知值对象
 * <p>
 * 寿险/重疾/医疗险核保的核心输入，对应 {@code UnderwritingInputType.HEALTH_DECLARATION}。
 * 采集被保人既往病史、家族遗传史、吸烟习惯及身高体重，用于评估健康风险。 值对象内聚 BMI 计算与健康风险评分逻辑（充血模型）。
 * </p>
 *
 * @param medicalHistory 既往病史列表（如高血压、糖尿病等），无则为空列表
 * @param familyHistory 家族遗传病史列表，无则为空列表
 * @param smoking 是否吸烟
 * @param heightCm 身高（厘米）
 * @param weightKg 体重（千克）
 * @author wei.sun
 * @since 2026/6/23
 */
public record HealthDeclaration(List<String> medicalHistory, List<String> familyHistory, boolean smoking,
                                BigDecimal heightCm, BigDecimal weightKg)
        implements
            Serializable {

    /**
     * 值对象名（用于校验异常上下文）
     */
    private static final String VO_NAME = "HealthDeclaration";

    public HealthDeclaration(List<String> medicalHistory, List<String> familyHistory, boolean smoking,
                             BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || heightCm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(VO_NAME, "heightCm", "身高必须大于零");
        }
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(VO_NAME, "weightKg", "体重必须大于零");
        }
        // 防御性拷贝，保证值对象不可变
        this.medicalHistory = medicalHistory == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(medicalHistory));
        this.familyHistory = familyHistory == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(familyHistory));
        this.smoking = smoking;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
    }

    /**
     * 计算 BMI 身体质量指数（体重kg / 身高m 的平方）
     *
     * @return BMI 值，保留 1 位小数
     */
    public BigDecimal bmi() {
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal denominator = heightM.multiply(heightM);
        return weightKg.divide(denominator, 1, RoundingMode.HALF_UP);
    }

    /**
     * 健康风险评分（0-100，越高风险越大）
     * <p>
     * 综合既往病史、家族史、吸烟习惯与 BMI 异常进行加权评分（充血模型）。
     * </p>
     *
     * @return 健康风险评分
     */
    public int riskScore() {
        int score = 0;
        // 既往病史：每项 +20
        score += medicalHistory.size() * 20;
        // 家族遗传史：每项 +10
        score += familyHistory.size() * 10;
        // 吸烟：+15
        if (smoking) {
            score += 15;
        }
        // BMI 异常：偏瘦(<18.5)或超重(>=28)各 +10，肥胖(>=32)再 +10
        BigDecimal bmi = bmi();
        if (bmi.compareTo(BigDecimal.valueOf(18.5)) < 0 || bmi.compareTo(BigDecimal.valueOf(28)) >= 0) {
            score += 10;
        }
        if (bmi.compareTo(BigDecimal.valueOf(32)) >= 0) {
            score += 10;
        }
        return Math.min(score, 100);
    }
}
