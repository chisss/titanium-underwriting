package com.titanium.underwriting.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 核保风险参数值对象测试（DSN-09 / m25-11）
 * <p>
 * 锁死三层语义：① {@link UnderwritingParameter#DEFAULT} 与改造前的历史硬编码取值逐一对应
 * （任何一项漂移都会静默改变存量核保结论）；② 紧凑构造器三段校验（评分项非负 / BMI 边界
 * 严格递增 / 风险阈值严格递增）；③ 判别式——同一健康告知在自定义参数下评分不同，证明
 * 评分权重与 BMI 分段确实由参数驱动，而非「换个常量名的巧合」。
 * </p>
 */
class UnderwritingParameterTest {

    // ── ① DEFAULT 与历史硬编码逐项对应 ─────────────────────────────────────

    @Test
    void defaultMatchesLegacyHardcodedValuesItemByItem() {
        UnderwritingParameter d = UnderwritingParameter.DEFAULT;
        assertEquals(20, d.medicalHistoryScore(), "既往病史每项得分漂移（历史硬编码 20）");
        assertEquals(10, d.familyHistoryScore(), "家族史每项得分漂移（历史硬编码 10）");
        assertEquals(15, d.smokingScore(), "吸烟得分漂移（历史硬编码 15）");
        assertDecimal("18.5", d.bmiLowerBound(), "BMI 偏瘦下限漂移（历史硬编码 18.5）");
        assertDecimal("28", d.bmiOverweightBound(), "BMI 超重界漂移（历史硬编码 28）");
        assertDecimal("32", d.bmiObesityBound(), "BMI 肥胖界漂移（历史硬编码 32）");
        assertEquals(10, d.bmiDeviationScore(), "BMI 偏离得分漂移（历史硬编码 10）");
        assertEquals(100, d.scoreCap(), "评分封顶漂移（历史硬编码 100）");
        assertEquals(30, d.subStandardThreshold(), "次标准体阈值漂移（历史硬编码 30，去重复硬编码的归并点）");
        assertEquals(60, d.highRiskThreshold(), "高风险阈值漂移（历史硬编码 60）");
        assertEquals(85, d.uninsurableThreshold(), "不可保阈值漂移（历史硬编码 85）");
    }

    // ── ② 紧凑构造器校验 ──────────────────────────────────────────────────

    @Test
    void rejectsNegativeScoreItems() {
        assertThrows(UnderwritingValidationException.class, () -> scoreParameter(-1, 10, 15, 10, 100),
                "medicalHistoryScore 为负须拒绝构造");
        assertThrows(UnderwritingValidationException.class, () -> scoreParameter(20, -1, 15, 10, 100),
                "familyHistoryScore 为负须拒绝构造");
        assertThrows(UnderwritingValidationException.class, () -> scoreParameter(20, 10, -1, 10, 100),
                "smokingScore 为负须拒绝构造");
        assertThrows(UnderwritingValidationException.class, () -> scoreParameter(20, 10, 15, -1, 100),
                "bmiDeviationScore 为负须拒绝构造");
    }

    @Test
    void rejectsNonPositiveScoreCap() {
        assertThrows(UnderwritingValidationException.class, () -> scoreParameter(20, 10, 15, 10, 0),
                "scoreCap 为零须拒绝构造（封顶为 0 意味着评分恒定 0，属无效配置）");
    }

    @Test
    void rejectsNullOrNonPositiveOrNonAscendingBmiBounds() {
        assertThrows(UnderwritingValidationException.class,
                () -> new UnderwritingParameter(20, 10, 15, null, new BigDecimal("28"), new BigDecimal("32"),
                        10, 100, 30, 60, 85),
                "BMI 边界为空须拒绝构造（绑定链路缺项会落 null）");
        assertThrows(UnderwritingValidationException.class,
                () -> new UnderwritingParameter(20, 10, 15, BigDecimal.ZERO, new BigDecimal("28"),
                        new BigDecimal("32"), 10, 100, 30, 60, 85),
                "BMI 边界非正须拒绝构造");
        assertThrows(UnderwritingValidationException.class,
                () -> new UnderwritingParameter(20, 10, 15, new BigDecimal("28"), new BigDecimal("18.5"),
                        new BigDecimal("32"), 10, 100, 30, 60, 85),
                "BMI 边界非递增（下限 ≥ 超重界）须拒绝构造");
        assertThrows(UnderwritingValidationException.class,
                () -> new UnderwritingParameter(20, 10, 15, new BigDecimal("18.5"), new BigDecimal("32"),
                        new BigDecimal("28"), 10, 100, 30, 60, 85),
                "BMI 边界非递增（超重界 ≥ 肥胖界）须拒绝构造");
    }

    @Test
    void rejectsNonPositiveOrNonAscendingThresholds() {
        assertThrows(UnderwritingValidationException.class, () -> thresholdParameter(0, 60, 85),
                "次标准体阈值非正须拒绝构造");
        assertThrows(UnderwritingValidationException.class, () -> thresholdParameter(60, 30, 85),
                "次标准体阈值 ≥ 高风险阈值须拒绝构造（等级区间为空）");
        assertThrows(UnderwritingValidationException.class, () -> thresholdParameter(30, 85, 60),
                "高风险阈值 ≥ 不可保阈值须拒绝构造（等级区间为空）");
    }

    // ── ③ 判别式：参数确实驱动评分 ────────────────────────────────────────

    @Test
    void customFamilyHistoryWeightChangesScoreOfSameDeclaration() {
        HealthDeclaration declaration = new HealthDeclaration(List.of(), List.of("糖尿病家族史"), false,
                new BigDecimal("170"), new BigDecimal("65"));

        assertEquals(10, declaration.riskScore(UnderwritingParameter.DEFAULT),
                "基线：1 项家族史按默认权重计 10 分");

        UnderwritingParameter heavier = withFamilyHistoryScore(35);
        assertEquals(35, declaration.riskScore(heavier),
                "判别式：权重调至 35 后同一告知评分须随之变化（评分权重未被参数驱动即判红）");
    }

    @Test
    void customBmiOverweightBoundChangesDeviationScoring() {
        // 170cm/80kg → BMI 27.7，恰在默认超重界 28 之下
        HealthDeclaration declaration = new HealthDeclaration(List.of(), List.of(), false,
                new BigDecimal("170"), new BigDecimal("80"));

        assertEquals(0, declaration.riskScore(UnderwritingParameter.DEFAULT),
                "基线：BMI 27.7 未达默认超重界 28，不计偏离");

        UnderwritingParameter strict = withBmiOverweightBound(new BigDecimal("24"));
        assertEquals(10, declaration.riskScore(strict),
                "判别式：超重界收紧至 24 后同一 BMI 须计一次偏离（BMI 分段未被参数驱动即判红）");
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    private static UnderwritingParameter scoreParameter(int medicalHistoryScore, int familyHistoryScore,
            int smokingScore, int bmiDeviationScore, int scoreCap) {
        return new UnderwritingParameter(medicalHistoryScore, familyHistoryScore, smokingScore,
                new BigDecimal("18.5"), new BigDecimal("28"), new BigDecimal("32"), bmiDeviationScore, scoreCap,
                30, 60, 85);
    }

    private static UnderwritingParameter thresholdParameter(int subStandardThreshold, int highRiskThreshold,
            int uninsurableThreshold) {
        return new UnderwritingParameter(20, 10, 15, new BigDecimal("18.5"), new BigDecimal("28"),
                new BigDecimal("32"), 10, 100, subStandardThreshold, highRiskThreshold, uninsurableThreshold);
    }

    private static UnderwritingParameter withFamilyHistoryScore(int value) {
        return new UnderwritingParameter(20, value, 15, new BigDecimal("18.5"), new BigDecimal("28"),
                new BigDecimal("32"), 10, 100, 30, 60, 85);
    }

    private static UnderwritingParameter withBmiOverweightBound(BigDecimal value) {
        return new UnderwritingParameter(20, 10, 15, new BigDecimal("18.5"), value,
                new BigDecimal("32"), 10, 100, 30, 60, 85);
    }

    private static void assertDecimal(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                message + "：expected=" + expected + ", actual=" + actual);
    }
}
