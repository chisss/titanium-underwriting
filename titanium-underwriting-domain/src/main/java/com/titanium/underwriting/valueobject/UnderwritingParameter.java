package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 核保风险参数值对象（DSN-09 / m25-11）
 * <p>
 * 内聚「内置加权评分」路径的全部可配置数值：健康告知评分的权重与 BMI 分段、
 * 风险等级阈值、次标准体加费基准（与次标准体阈值同源，历史重复硬编码 30 归并于
 * {@link #subStandardThreshold}）。默认值 {@link #DEFAULT} 与历史硬编码取值逐一对应——
 * 不配置任何覆盖时行为与改造前完全一致。
 * </p>
 * <p>
 * 运行时由 application 层编排器经配置 {@code titanium.underwriting.risk-parameter}
 * （支持租户级覆盖，见 {@code UnderwritingParameterProperties}）解析后充实进核保命令；
 * 命令未携带参数（未经编排的直连路径）时由聚合根回退 {@link #DEFAULT}。
 * </p>
 * <p>
 * 🔴 本值对象只影响「未来决策」——不随事件落盘、不参与事件回放（评分结果已固化在
 * {@code UnderwritingDecidedEvent.riskScore} 等事件字段中），调整参数不改写历史结论。
 * </p>
 *
 * @param medicalHistoryScore 健康告知：每项既往病史得分（默认 20）
 * @param familyHistoryScore 健康告知：每项家族遗传病史得分（默认 10）
 * @param smokingScore 健康告知：吸烟得分（默认 15）
 * @param bmiLowerBound BMI 偏瘦下限（低于该值计一次偏离；默认 18.5）
 * @param bmiOverweightBound BMI 超重界（达到即计一次偏离；默认 28）
 * @param bmiObesityBound BMI 肥胖界（达到再计一次偏离；默认 32）
 * @param bmiDeviationScore 每次 BMI 偏离得分（默认 10）
 * @param scoreCap 健康告知评分封顶（默认 100）
 * @param subStandardThreshold 次标准体风险阈值（评分 ≥ 该值判次标准体；兼作加费基准，默认 30）
 * @param highRiskThreshold 高风险体风险阈值（默认 60）
 * @param uninsurableThreshold 不可保体风险阈值（默认 85）
 * @author wei.sun
 * @since 2026/10/15
 */
public record UnderwritingParameter(
        int medicalHistoryScore,
        int familyHistoryScore,
        int smokingScore,
        BigDecimal bmiLowerBound,
        BigDecimal bmiOverweightBound,
        BigDecimal bmiObesityBound,
        int bmiDeviationScore,
        int scoreCap,
        int subStandardThreshold,
        int highRiskThreshold,
        int uninsurableThreshold)
        implements
            Serializable {

    /**
     * 值对象名（用于校验异常上下文）
     */
    private static final String VO_NAME = "UnderwritingParameter";

    /**
     * 默认参数集：与历史硬编码取值逐一对应（DSN-09 改造前的系统行为基线）
     */
    public static final UnderwritingParameter DEFAULT = of(
            20, 10, 15,
            new BigDecimal("18.5"), new BigDecimal("28"), new BigDecimal("32"),
            10, 100,
            30, 60, 85);

    /**
     * 全参工厂（受控构建入口）
     * <p>
     * 11 个分量中含 4 个相邻 int 权重与 3 个相邻 BigDecimal 边界，直接 {@code new} 极易
     * 位置错位且编译器无法拦截；>8 组件 record 须提供公开静态工厂为 DSN-04 架构门禁
     * （{@code bigRecordsMustProvideFactoryOrBuilder}）的强制要求。分量语义见 record 头
     * javadoc，取值合法性由紧凑构造器统一校验。
     * </p>
     */
    public static UnderwritingParameter of(
            int medicalHistoryScore, int familyHistoryScore, int smokingScore,
            BigDecimal bmiLowerBound, BigDecimal bmiOverweightBound, BigDecimal bmiObesityBound,
            int bmiDeviationScore, int scoreCap,
            int subStandardThreshold, int highRiskThreshold, int uninsurableThreshold) {
        return new UnderwritingParameter(medicalHistoryScore, familyHistoryScore, smokingScore,
                bmiLowerBound, bmiOverweightBound, bmiObesityBound,
                bmiDeviationScore, scoreCap, subStandardThreshold, highRiskThreshold, uninsurableThreshold);
    }

    public UnderwritingParameter {
        validateScores(medicalHistoryScore, familyHistoryScore, smokingScore, bmiDeviationScore, scoreCap);
        validateBmiBounds(bmiLowerBound, bmiOverweightBound, bmiObesityBound);
        validateThresholds(subStandardThreshold, highRiskThreshold, uninsurableThreshold);
    }

    /** 校验评分项：各项分值非负、封顶为正 */
    private static void validateScores(int medicalHistoryScore, int familyHistoryScore, int smokingScore,
                                       int bmiDeviationScore, int scoreCap) {
        if (medicalHistoryScore < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "medicalHistoryScore 须非负");
        }
        if (familyHistoryScore < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "familyHistoryScore 须非负");
        }
        if (smokingScore < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "smokingScore 须非负");
        }
        if (bmiDeviationScore < 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "bmiDeviationScore 须非负");
        }
        if (scoreCap <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "scoreCap 须为正");
        }
    }

    /** 校验 BMI 分段边界：非空、为正、严格递增（偏瘦下限 < 超重界 < 肥胖界） */
    private static void validateBmiBounds(BigDecimal bmiLowerBound, BigDecimal bmiOverweightBound,
                                          BigDecimal bmiObesityBound) {
        if (bmiLowerBound == null || bmiOverweightBound == null || bmiObesityBound == null) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "BMI 分段边界不可为空");
        }
        if (bmiLowerBound.signum() <= 0 || bmiOverweightBound.signum() <= 0 || bmiObesityBound.signum() <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "BMI 分段边界须为正");
        }
        if (bmiLowerBound.compareTo(bmiOverweightBound) >= 0
                || bmiOverweightBound.compareTo(bmiObesityBound) >= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "BMI 分段边界须严格递增（偏瘦下限 < 超重界 < 肥胖界）");
        }
    }

    /** 校验风险等级阈值：为正且严格递增（次标准体 < 高风险 < 不可保），保证各等级区间非空 */
    private static void validateThresholds(int subStandardThreshold, int highRiskThreshold,
                                           int uninsurableThreshold) {
        if (subStandardThreshold <= 0 || subStandardThreshold >= highRiskThreshold
                || highRiskThreshold >= uninsurableThreshold) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED, VO_NAME,
                    "风险等级阈值须满足 0 < subStandardThreshold < highRiskThreshold < uninsurableThreshold");
        }
    }
}
