package com.titanium.underwriting.valueobject;

import java.io.Serializable;
import java.math.BigDecimal;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 被保人粗粒度风险要素值对象（承保前置链路的核保入口要素）
 * <p>
 * 承载「年龄 / 性别 / 职业类别 / BMI」四项<b>粗粒度</b>风险要素，是出单主链路
 * （policy 投保单 → 核保）在自动核保时能够真实提供的<b>全部</b>要素。
 * </p>
 * <p>
 * <b>为何不直接放进既有四个险种输入块</b>：{@link HealthDeclaration} / {@link PhysicalExamResult} /
 * {@link OccupationInfo} / {@link FinancialAssessment} / {@link VehicleRiskInfo} 各自承载的是
 * <b>完整明细</b>，其紧凑构造器持有「字段齐全」类不变量——例如 {@link OccupationInfo} 要求
 * {@code occupationName} 非空且 {@code riskFactor} 非空，{@link PhysicalExamResult} 要求
 * BMI 与收缩压/舒张压/血糖四项齐全。而承保前置链路手头只有「职业类别」与「BMI」两个离散值，
 * 构造不出任何一个完整块，只能整块留空（改造前 {@code SyncUnderwritingDecisionAdapter} 正是
 * 只填部分字段，导致被装配守卫整块丢弃、核保域恒收空输入）。强行把明细块放宽为「字段可缺」
 * 会摧毁这些值对象本就该有的不变量，故按<b>粒度</b>另立本值对象。
 * </p>
 * <p>
 * <b>与明细块的关系</b>：二者可同时存在（明细来自柜台/APP 完整录入，本类来自出单链路自动透传）。
 * {@link UnderwritingInput#aggregateRiskScore()} 取各项<b>最大值</b>而非求和，故同一要素
 * 在两处出现不会重复计分。
 * </p>
 * <p>
 * TODO 规则引擎接入：本类的年龄/职业/BMI 权重与同域其它输入块一致，均为<b>内置占位规则</b>，
 * 后续应随 {@link UnderwritingInput} 一并下沉至 titanium-rule-engine 按租户/险种配置。
 * </p>
 *
 * @param age                被保人年龄（可为 null；提供时须在 0-150 之间）
 * @param gender             被保人性别（可为 null）
 * @param occupationCategory 职业类别（可为 null；提供时须在 1-6 之间）
 * @param bmi                身体质量指数（可为 null；提供时须大于 0）
 * @author wei.sun
 * @since 2026/9/26
 */
public record InsuredRiskFactors(Integer age, CustomerGender gender, Integer occupationCategory,
                                 BigDecimal bmi)
        implements
            Serializable {

    /**
     * 值对象名（用于校验异常上下文）
     */
    private static final String VO_NAME = "InsuredRiskFactors";

    /**
     * 年龄下限（0 岁，即出生未满周岁可承保的险种）
     */
    private static final int MIN_AGE = 0;

    /**
     * 年龄上限（超过即视为录入错误而非真实被保人）
     */
    private static final int MAX_AGE = 150;

    /** 最低职业类别 */
    private static final int MIN_OCCUPATION_CATEGORY = 1;

    /** 最高职业类别（拒保类） */
    private static final int MAX_OCCUPATION_CATEGORY = 6;

    /** 年龄加分起始档：>= 该年龄加 {@link #AGE_SCORE_SENIOR} */
    private static final int AGE_SENIOR = 60;

    /** 年龄加分起始档：>= 该年龄加 {@link #AGE_SCORE_MATURE} */
    private static final int AGE_MATURE = 50;

    /** 年龄加分起始档：>= 该年龄加 {@link #AGE_SCORE_ADULT} */
    private static final int AGE_ADULT = 40;

    /** 高龄加分 */
    private static final int AGE_SCORE_SENIOR = 30;

    /** 中高龄加分 */
    private static final int AGE_SCORE_MATURE = 20;

    /** 中年加分 */
    private static final int AGE_SCORE_ADULT = 10;

    /** 每档职业类别加分（与 {@link OccupationInfo#riskScore()} 同口径） */
    private static final int OCCUPATION_SCORE_PER_LEVEL = 15;

    /** BMI 超重/肥胖加分（与 {@link PhysicalExamResult#riskScore()} 同口径） */
    private static final int BMI_SCORE_OVERWEIGHT = 15;

    /** BMI 超重阈值 */
    private static final BigDecimal BMI_OVERWEIGHT = BigDecimal.valueOf(28);

    /**
     * 紧凑构造器：校验各项不为空时的取值合法性
     * <p>
     * 全部分量均可为 {@code null}（表示该要素未提供）。四项全空在业务上等价于「未提供任何
     * 粗粒度要素」，是否允许由调用方裁决（见 {@link #hasAny()}），本类自身不拒绝全空——
     * 空容器是合法的中间态，拒绝它会让「部分要素先到、其余后到」的分次提交无法表达。
     * </p>
     */
    public InsuredRiskFactors {
        if (age != null && (age < MIN_AGE || age > MAX_AGE)) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.AGE_INVALID, VO_NAME, "age");
        }
        if (occupationCategory != null
                && (occupationCategory < MIN_OCCUPATION_CATEGORY || occupationCategory > MAX_OCCUPATION_CATEGORY)) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.OCCUPATION_CATEGORY_INVALID, VO_NAME,
                    "occupationCategory");
        }
        if (bmi != null && bmi.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingValidationException(UnderwritingErrorCode.BMI_POSITIVE, VO_NAME, "bmi");
        }
    }

    /**
     * 是否已提供任一粗粒度要素
     *
     * @return true 表示至少存在一项要素
     */
    public boolean hasAny() {
        return age != null || gender != null || occupationCategory != null || bmi != null;
    }

    /**
     * 粗粒度要素风险评分（0-100，越高风险越大）
     * <p>
     * 综合年龄档位、职业类别与 BMI 异常加权评分（充血模型），口径与同域明细输入块一致：
     * 职业按每类 +{@value #OCCUPATION_SCORE_PER_LEVEL}、BMI 超重 +{@value #BMI_SCORE_OVERWEIGHT}。
     * </p>
     * <p>
     * 🔴 <b>性别刻意不参与评分</b>：性别在本域是<b>评级因子</b>（影响费率厘定）而非
     * <b>核保风险因子</b>（影响是否承保/加费），且差异化定价须有精算依据与合规审查。
     * 故本类只<b>承载并传递</b>性别供下游规则引擎使用，不在硬编码占位评分中注入性别权重——
     * 需要性别加权的险种应由规则引擎按产品配置声明。
     * </p>
     *
     * @return 风险评分；未提供任何要素时返回 0
     */
    public int riskScore() {
        int score = 0;
        // 年龄档位：高龄风险递增（0/10/20/30）
        if (age != null) {
            if (age >= AGE_SENIOR) {
                score += AGE_SCORE_SENIOR;
            } else if (age >= AGE_MATURE) {
                score += AGE_SCORE_MATURE;
            } else if (age >= AGE_ADULT) {
                score += AGE_SCORE_ADULT;
            }
        }
        // 职业类别 1-6 → 0/15/30/45/60/75
        if (occupationCategory != null) {
            score += (occupationCategory - MIN_OCCUPATION_CATEGORY) * OCCUPATION_SCORE_PER_LEVEL;
        }
        // BMI 超重/肥胖
        if (bmi != null && bmi.compareTo(BMI_OVERWEIGHT) >= 0) {
            score += BMI_SCORE_OVERWEIGHT;
        }
        return Math.min(score, 100);
    }
}
