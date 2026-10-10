package com.titanium.underwriting.valueobject;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 被保人粗粒度风险要素值对象测试（G02/AC-01）
 * <p>
 * 覆盖 {@link InsuredRiskFactors} 的取值校验、{@link InsuredRiskFactors#hasAny()} 空容器语义、
 * 风险评分口径，以及其并入 {@link UnderwritingInput#aggregateRiskScore()} 后的<b>不重复计分</b>断言。
 * </p>
 * <p>
 * 🔴 其中「性别不参与评分」一条锁的是 javadoc 声明的设计语义（性别是<b>评级因子</b>而非
 * <b>核保风险因子</b>），若后续有人给性别加权重，本用例会失败——那正是需要重新论证精算依据
 * 与合规审查的时刻，不应径直改断言。
 * </p>
 */
class InsuredRiskFactorsTest {

    @Test
    @DisplayName("四要素齐全：年龄档+职业类别+BMI 累加评分，且风险等级非 STANDARD")
    void scoresAgeOccupationAndBmiWhenAllPresent() {
        // 45 岁(+10) + 职业 4 类((4-1)*15=45) + BMI 29 超重(+15) = 70
        InsuredRiskFactors factors = new InsuredRiskFactors(45, CustomerGender.MALE, 4, new BigDecimal("29"));
        if (factors.riskScore() != 70) {
            throw new AssertionError("45岁/职业4类/BMI29 应为 70 分，实际=" + factors.riskScore());
        }
        // 并入容器后 70 >= 60（高风险阈值），不得再是 STANDARD
        UnderwritingInput input = UnderwritingInput.builder().insuredRiskFactors(factors).build();
        if (!input.hasAnyInput()) {
            throw new AssertionError("已提供粗粒度要素，hasAnyInput 应为 true");
        }
        if (input.assessRiskLevel() == UnderwritingEnum.RiskLevel.STANDARD) {
            throw new AssertionError("70 分不应判为标准体，实际=" + input.assessRiskLevel());
        }
    }

    @Test
    @DisplayName("年龄按档位加分：40/50/60 三档分别为 10/20/30，未达 40 岁不加分")
    void scoresAgeByBracket() {
        assertScore(new InsuredRiskFactors(39, null, null, null), 0);
        assertScore(new InsuredRiskFactors(40, null, null, null), 10);
        assertScore(new InsuredRiskFactors(50, null, null, null), 20);
        assertScore(new InsuredRiskFactors(60, null, null, null), 30);
    }

    @Test
    @DisplayName("BMI 仅达 28 才加分，未达阈值不加分")
    void scoresBmiOnlyAtOverweightThreshold() {
        assertScore(new InsuredRiskFactors(null, null, null, new BigDecimal("27.9")), 0);
        assertScore(new InsuredRiskFactors(null, null, null, new BigDecimal("28")), 15);
    }

    @Test
    @DisplayName("🔴 性别不参与评分：其余要素不变时仅改性别，评分不变")
    void genderNeverAffectsRiskScore() {
        InsuredRiskFactors male = new InsuredRiskFactors(45, CustomerGender.MALE, 4, new BigDecimal("29"));
        InsuredRiskFactors female = new InsuredRiskFactors(45, CustomerGender.FEMALE, 4, new BigDecimal("29"));
        InsuredRiskFactors unknown = new InsuredRiskFactors(45, CustomerGender.UNKNOWN, 4, new BigDecimal("29"));
        if (male.riskScore() != female.riskScore() || male.riskScore() != unknown.riskScore()) {
            throw new AssertionError("性别是评级因子而非风险因子，不得影响核保评分：MALE=" + male.riskScore()
                    + ", FEMALE=" + female.riskScore() + ", UNKNOWN=" + unknown.riskScore());
        }
        // 性别仅承载传递，仍须如实保留在值对象中（供规则引擎按产品配置取用）
        if (female.gender() != CustomerGender.FEMALE) {
            throw new AssertionError("性别应被原样承载，实际=" + female.gender());
        }
    }

    @Test
    @DisplayName("评分上限 100：高龄+高职业类别+BMI 超重不得溢出")
    void capsRiskScoreAtHundred() {
        // 65岁(+30) + 6类((6-1)*15=75) + BMI 30(+15) = 120 → 100
        assertScore(new InsuredRiskFactors(65, null, 6, new BigDecimal("30")), 100);
    }

    @Test
    @DisplayName("四项全空是合法中间态：不抛异常、hasAny 为 false")
    void allowsAllNullAsLegalIntermediateState() {
        InsuredRiskFactors empty = new InsuredRiskFactors(null, null, null, null);
        if (empty.hasAny()) {
            throw new AssertionError("四项全空时 hasAny 应为 false");
        }
        if (empty.riskScore() != 0) {
            throw new AssertionError("无要素时评分应为 0，实际=" + empty.riskScore());
        }
    }

    @Test
    @DisplayName("任一项非空即 hasAny 为 true（含仅性别）")
    void hasAnyWhenSingleFactorPresent() {
        if (!new InsuredRiskFactors(null, CustomerGender.MALE, null, null).hasAny()) {
            throw new AssertionError("仅提供性别时 hasAny 应为 true");
        }
        if (!new InsuredRiskFactors(30, null, null, null).hasAny()) {
            throw new AssertionError("仅提供年龄时 hasAny 应为 true");
        }
    }

    @Test
    @DisplayName("取值校验：年龄越界/职业类别越界/BMI 非正一律拒绝构造")
    void rejectsOutOfRangeValues() {
        assertRejected(UnderwritingErrorCode.AGE_INVALID, () -> new InsuredRiskFactors(-1, null, null, null));
        assertRejected(UnderwritingErrorCode.AGE_INVALID, () -> new InsuredRiskFactors(151, null, null, null));
        assertRejected(UnderwritingErrorCode.OCCUPATION_CATEGORY_INVALID,
                () -> new InsuredRiskFactors(null, null, 0, null));
        assertRejected(UnderwritingErrorCode.OCCUPATION_CATEGORY_INVALID,
                () -> new InsuredRiskFactors(null, null, 7, null));
        assertRejected(UnderwritingErrorCode.BMI_POSITIVE,
                () -> new InsuredRiskFactors(null, null, null, BigDecimal.ZERO));
        assertRejected(UnderwritingErrorCode.BMI_POSITIVE,
                () -> new InsuredRiskFactors(null, null, null, new BigDecimal("-1")));
        // 边界值合法
        new InsuredRiskFactors(0, null, 1, new BigDecimal("0.1"));
        new InsuredRiskFactors(150, null, 6, new BigDecimal("100"));
    }

    @Test
    @DisplayName("同一要素同时出现在粗粒度容器与明细块时不重复计分（取最大值）")
    void doesNotDoubleCountSameFactorAcrossBlocks() {
        // 明细块：职业 2 类 → (2-1)*15=15，危险系数 1.0 不额外加分
        OccupationInfo occupation = new OccupationInfo("内勤", 2, new BigDecimal("1.0"));
        // 粗粒度容器：同一职业类别 → 15
        InsuredRiskFactors factors = new InsuredRiskFactors(null, null, 2, null);
        UnderwritingInput input = UnderwritingInput.builder().occupationInfo(occupation).insuredRiskFactors(factors)
                .build();
        if (input.aggregateRiskScore() != 15) {
            throw new AssertionError("同一职业类别在两处出现应取最大值 15 而非求和 30，实际="
                    + input.aggregateRiskScore());
        }
    }

    @Test
    @DisplayName("明细块为空但粗粒度要素在场时，容器仍视为有输入")
    void countsCoarseFactorsAsPresentInput() {
        // 改造前承保前置链路只能填出这样的请求：明细块因字段不全被整块丢弃，若粗粒度要素
        // 不计入 hasAnyInput，核保域会退化为「无输入 → 按金额兜底」，正是本次要消除的路径
        UnderwritingInput input = UnderwritingInput.builder()
                .insuredRiskFactors(new InsuredRiskFactors(45, CustomerGender.MALE, 4, new BigDecimal("29"))).build();
        if (!input.hasAnyInput()) {
            throw new AssertionError("仅粗粒度要素在场时 hasAnyInput 应为 true");
        }
    }

    private void assertScore(InsuredRiskFactors factors, int expected) {
        if (factors.riskScore() != expected) {
            throw new AssertionError("预期评分 " + expected + "，实际=" + factors.riskScore() + "（要素=" + factors + "）");
        }
    }

    private void assertRejected(UnderwritingErrorCode expectedCode, Runnable construction) {
        try {
            construction.run();
            throw new AssertionError("非法取值应拒绝构造，实际通过（预期错误码=" + expectedCode.getCode() + "）");
        } catch (UnderwritingValidationException ex) {
            // 直接比对枚举（而非 code 字符串）——裸串构造器会落入 DEFAULT_ERROR_CODE，比对枚举才能识破
            if (ex.getErrorCode() != expectedCode) {
                throw new AssertionError("预期错误码 " + expectedCode + "，实际=" + ex.getErrorCode());
            }
        }
    }
}
