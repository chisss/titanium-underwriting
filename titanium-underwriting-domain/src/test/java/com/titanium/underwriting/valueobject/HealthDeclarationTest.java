package com.titanium.underwriting.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.underwriting.HealthDeclarationQuestion;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 健康告知值对象测试（G12/g12-01 AC-01）
 * <p>
 * 锁死三组语义：① <b>告知内容可观察到决策差异</b>——「带既往病史」与「不带」的评分必须不同（AC-01
 * 的「核保决策入参可观察到差异」在核保域侧的落点）；② 评分规则逐项边界（病史 20/项、家族史 10/项、
 * 吸烟 15、BMI 三档罚分、封顶 100）；③ 构造不变量（身高体重必须为正）与不可变性。
 * </p>
 */
class HealthDeclarationTest {

    // ---------------------------------------------------------------- AC-01：告知差异可观察

    @Test
    void observesScoreDifferenceBetweenDeclarationsWithAndWithoutHistory() {
        // 同一被保人（同身高体重）：「既往病史 = 高血压」与不带，评分必须可观察到差异。
        // 这正是 AC-01 要求的「同一投保单，带与不带，核保决策入参可观察到差异」的评分侧证据——
        // 若该块在链路中途丢失（改造前的断链），两者会得到同一个分数，本断言即失败
        HealthDeclaration withHistory = health(List.of("高血压"), List.of(), false, "175", "80");
        HealthDeclaration withoutHistory = health(List.of(), List.of(), false, "175", "80");

        assertTrue(withHistory.riskScore() > withoutHistory.riskScore());
        assertEquals(20, withHistory.riskScore() - withoutHistory.riskScore());
    }

    // ---------------------------------------------------------------- 评分规则

    @Test
    void computesBmiAsWeightOverSquaredHeightInMeters() {
        assertEquals(new BigDecimal("26.1"), health(List.of(), List.of(), false, "175", "80").bmi());
        assertEquals(new BigDecimal("22.9"), health(List.of(), List.of(), false, "175", "70").bmi());
    }

    @Test
    void scoresEachHistoryItemAndSmokingHabit() {
        // 既往病史每项 +20、家族史每项 +10、吸烟 +15；BMI 26.1 属正常区间不罚
        HealthDeclaration declaration = health(List.of("高血压", "糖尿病"), List.of("冠心病家族史"), true, "175", "80");

        assertEquals(2 * 20 + 10 + 15, declaration.riskScore());
    }

    @Test
    void penalizesUnderweightOverweightAndObesityBands() {
        // <18.5 → +10；[28,32) → +10；>=32 → +20（两档叠加）；正常区间（175/80，BMI 26.1）为对照不罚
        assertEquals(10, health(List.of(), List.of(), false, "175", "50").riskScore());
        assertEquals(10, health(List.of(), List.of(), false, "175", "86").riskScore());
        assertEquals(20, health(List.of(), List.of(), false, "175", "100").riskScore());
        assertEquals(0, health(List.of(), List.of(), false, "175", "80").riskScore());
    }

    @Test
    void capsScoreAtOneHundred() {
        HealthDeclaration declaration = health(
                List.of("病史1", "病史2", "病史3", "病史4", "病史5", "病史6"), List.of(), true, "175", "100");

        // 未封顶时 = 6*20 + 15 + 20 = 155
        assertEquals(100, declaration.riskScore());
    }

    // ---------------------------------------------------------------- 构造不变量与不可变性

    @Test
    void rejectsMissingNonPositiveOrNegativeHeightAndWeight() {
        assertThrows(UnderwritingValidationException.class,
                () -> new HealthDeclaration(List.of(), List.of(), false, null, new BigDecimal("80")));
        assertThrows(UnderwritingValidationException.class,
                () -> new HealthDeclaration(List.of(), List.of(), false, BigDecimal.ZERO, new BigDecimal("80")));
        assertThrows(UnderwritingValidationException.class,
                () -> new HealthDeclaration(List.of(), List.of(), false, new BigDecimal("175"), new BigDecimal("-1")));
    }

    @Test
    void copiesHistoryDefensivelyAndTreatsNullListsAsEmpty() {
        List<String> mutable = new ArrayList<>(List.of("高血压"));
        HealthDeclaration declaration = new HealthDeclaration(mutable, null, false, new BigDecimal("175"),
                new BigDecimal("80"));

        // 构造后修改源列表不得影响值对象（跨聚合传递不可变）；null 列表按空承接
        mutable.add("糖尿病");
        assertEquals(List.of("高血压"), declaration.medicalHistory());
        assertEquals(List.of(), declaration.familyHistory());
        assertThrows(UnsupportedOperationException.class, () -> declaration.medicalHistory().add("x"));
    }

    // ---------------------------------------------------------------- 自定义告知项答案（G12/g12-02）

    @Test
    void treatsConfiguredAnswersAsProvidedInOrder() {
        HealthDeclaration declaration = new HealthDeclaration(List.of(), List.of(), false, new BigDecimal("175"),
                new BigDecimal("80"),
                List.of(new HealthDeclarationAnswer(HealthDeclarationQuestion.HOSPITALIZATION_TWO_YEARS, "true"),
                        new HealthDeclarationAnswer(HealthDeclarationQuestion.SURGERY_HISTORY, "false")));

        assertEquals(2, declaration.answers().size());
        assertEquals(HealthDeclarationQuestion.HOSPITALIZATION_TWO_YEARS, declaration.answers().get(0).question());
        assertEquals("true", declaration.answers().get(0).answer());
        assertEquals("false", declaration.answers().get(1).answer());
    }

    @Test
    void treatsMissingAnswersAsEmptyListAndCopiesDefensively() {
        // 兼容构造器（未携带答案）与显式 null 两态同为空列表——「未提供」与「提供了零项」语义同形
        assertEquals(List.of(), health(List.of(), List.of(), false, "175", "80").answers());
        assertEquals(List.of(), new HealthDeclaration(List.of(), List.of(), false, new BigDecimal("175"),
                new BigDecimal("80"), null).answers());

        List<HealthDeclarationAnswer> mutable = new ArrayList<>(
                List.of(new HealthDeclarationAnswer(HealthDeclarationQuestion.CHRONIC_DISEASE, "true")));
        HealthDeclaration declaration = new HealthDeclaration(List.of(), List.of(), false, new BigDecimal("175"),
                new BigDecimal("80"), mutable);

        mutable.clear();
        assertEquals(1, declaration.answers().size(), "构造后修改源列表不得影响值对象");
        assertThrows(UnsupportedOperationException.class, () -> declaration.answers()
                .add(new HealthDeclarationAnswer(HealthDeclarationQuestion.DRUG_ALLERGY, "true")));
    }

    // ---------------------------------------------------------------- 夹具

    private HealthDeclaration health(List<String> medicalHistory, List<String> familyHistory, boolean smoking,
                                     String heightCm, String weightKg) {
        return new HealthDeclaration(medicalHistory, familyHistory, smoking, new BigDecimal(heightCm),
                new BigDecimal(weightKg));
    }
}
