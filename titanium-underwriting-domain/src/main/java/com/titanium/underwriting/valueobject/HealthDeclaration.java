package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

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
 * @param answers 自定义告知项答案清单（G12/g12-02；无则空列表）
 * @author wei.sun
 * @since 2026/6/23
 */
public record HealthDeclaration(List<String> medicalHistory, List<String> familyHistory, boolean smoking,
                                BigDecimal heightCm, BigDecimal weightKg, List<HealthDeclarationAnswer> answers)
        implements
            Serializable {

    /**
     * 值对象名（用于校验异常上下文）
     */
    private static final String VO_NAME = "HealthDeclaration";

    public HealthDeclaration {
        if (heightCm == null || heightCm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.HEIGHT_POSITIVE, VO_NAME, "heightCm");
        }
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.WEIGHT_POSITIVE, VO_NAME, "weightKg");
        }
        // 防御性拷贝，保证值对象不可变（紧凑构造器改形参，末尾由编译器赋值字段）
        medicalHistory = medicalHistory == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(medicalHistory));
        familyHistory = familyHistory == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(familyHistory));
        answers = answers == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(answers));
    }

    /**
     * 兼容旧版调用方的构造器（无自定义告知项答案，G12/g12-02）。
     * <p>答案清单按空列表处理——「未提供告知项答案」与「提供了零项」在评估语义上同形。</p>
     */
    public HealthDeclaration(List<String> medicalHistory, List<String> familyHistory, boolean smoking,
                             BigDecimal heightCm, BigDecimal weightKg) {
        this(medicalHistory, familyHistory, smoking, heightCm, weightKg, null);
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
     * 综合既往病史、家族史、吸烟习惯与 BMI 异常进行加权评分（充血模型）。权重与 BMI 分段
     * 取自 {@link UnderwritingParameter}（DSN-09 / m25-11）——数值经配置外部化并支持租户级覆盖，
     * 未覆盖时取 {@link UnderwritingParameter#DEFAULT}（与改造前硬编码行为一致）。
     * </p>
     *
     * @param parameter 核保风险参数（不可为 null；调用方未持有租户参数时应显式传
     *                  {@link UnderwritingParameter#DEFAULT}，禁止再新增无参重载）
     * @return 健康风险评分
     */
    public int riskScore(UnderwritingParameter parameter) {
        int score = 0;
        // 既往病史：每项计 parameter.medicalHistoryScore()
        score += medicalHistory.size() * parameter.medicalHistoryScore();
        // 家族遗传史：每项计 parameter.familyHistoryScore()
        score += familyHistory.size() * parameter.familyHistoryScore();
        // 吸烟
        if (smoking) {
            score += parameter.smokingScore();
        }
        // BMI 异常：偏瘦(< bmiLowerBound)或超重(>= bmiOverweightBound)计一次偏离，肥胖(>= bmiObesityBound)再计一次
        BigDecimal bmi = bmi();
        if (bmi.compareTo(parameter.bmiLowerBound()) < 0
                || bmi.compareTo(parameter.bmiOverweightBound()) >= 0) {
            score += parameter.bmiDeviationScore();
        }
        if (bmi.compareTo(parameter.bmiObesityBound()) >= 0) {
            score += parameter.bmiDeviationScore();
        }
        return Math.min(score, parameter.scoreCap());
    }
}
