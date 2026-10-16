package com.titanium.underwriting.aggregate;

import static org.axonframework.test.matchers.Matchers.matches;
import static org.axonframework.test.matchers.Matchers.messageWithPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.command.DecideUnderwritingCommand;
import com.titanium.underwriting.common.enums.ProductConfigSource;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.HealthDeclaration;
import com.titanium.underwriting.valueobject.InsuredRiskFactors;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.valueobject.UnderwritingParameter;

/**
 * 核保参数租户覆盖的聚合级判别式测试（DSN-09 / m25-11）
 * <p>
 * 🔴 判别点：<b>同一核保输入，仅命令携带的 {@link UnderwritingParameter} 不同，核保结论必须不同</b>。
 * 这是「参数确实从命令流入聚合决策」的唯一直接判别式——若聚合忽略命令参数恒用
 * {@link UnderwritingParameter#DEFAULT}（回退分支被错误地做成唯一分支），本类除基线用例外全部判红；
 * 而单纯「默认值抽成值对象」的改造即使参数被完全忽略，其他测试面（默认值断言、投影、既有回归）
 * 仍会全绿，无法暴露该缺陷。
 * </p>
 * <p>
 * 输入基线：职业类别 4 → 内置评分 (4−1)×15 = 45（次标准体区间 [30,60) 内）；
 * 健康告知基线：1 项家族史（默认权重 10）+ BMI 22.5 正常 → 评分 10。
 * </p>
 */
class UnderwritingParameterOverrideTest {

    private static final UnderwritingId UNDERWRITING_ID = new UnderwritingId("UW-PARAM-001");
    private static final String         TENANT_ID       = "TENANT-001";

    private FixtureConfiguration<Underwriting> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Underwriting.class);
        fixture.setReportIllegalStateChange(false);
    }

    // ── 基线：默认参数下结论与改造前一致 ──────────────────────────────────

    @Test
    void defaultParameterReproducesLegacyConclusionForSameInput() {
        // 评分 45：默认阈值 [30,60) → 次标准体 MODIFY/RATED，加费率 (45−30)×0.02 = 0.30
        expectDecision(occupationCategoryFourInput(), UnderwritingParameter.DEFAULT,
                UnderwritingEnum.RiskLevel.SUB_STANDARD, UnderwritingEnum.ConclusionType.MODIFY,
                UnderwritingEnum.UnderwritingStatus.RATED, new BigDecimal("0.30"));
    }

    // ── 判别式一：阈值覆盖翻转结论（同一输入） ────────────────────────────

    @Test
    void subStandardThresholdOverrideMovesSameInputToStandardBody() {
        // 次标准体阈值 30→50：评分 45 < 50 → 标准体 ACCEPT/STANDARD（若聚合未消费命令参数则仍判 MODIFY）
        expectDecision(occupationCategoryFourInput(), thresholdParameter(50, 60, 85),
                UnderwritingEnum.RiskLevel.STANDARD, UnderwritingEnum.ConclusionType.ACCEPT,
                UnderwritingEnum.UnderwritingStatus.STANDARD, null);
    }

    @Test
    void highRiskThresholdOverrideElevatesSameInputToPostpone() {
        // 高风险阈值 60→40：评分 45 ≥ 40 → 高风险 POSTPONE/POSTPONED（若聚合未消费命令参数则仍判 MODIFY）
        expectDecision(occupationCategoryFourInput(), thresholdParameter(30, 40, 85),
                UnderwritingEnum.RiskLevel.HIGH_RISK, UnderwritingEnum.ConclusionType.POSTPONE,
                UnderwritingEnum.UnderwritingStatus.POSTPONED, null);
    }

    // ── 判别式二：健康告知评分权重覆盖翻转结论（同一告知） ────────────────

    @Test
    void healthDeclarationUnderDefaultWeightsStaysStandardBody() {
        // 1 项家族史按默认权重 10 分 → 标准体 ACCEPT/STANDARD、无加费
        expectDecision(oneFamilyHistoryInput(), UnderwritingParameter.DEFAULT,
                UnderwritingEnum.RiskLevel.STANDARD, UnderwritingEnum.ConclusionType.ACCEPT,
                UnderwritingEnum.UnderwritingStatus.STANDARD, null);
    }

    @Test
    void healthDeclarationUnderHeavierWeightsBecomesSubStandardBody() {
        // 家族史权重 10→35：同一告知 35 分 ≥ 30 → 次标准体 MODIFY/RATED，加费率 (35−30)×0.02 = 0.10
        expectDecision(oneFamilyHistoryInput(), withFamilyHistoryScore(35),
                UnderwritingEnum.RiskLevel.SUB_STANDARD, UnderwritingEnum.ConclusionType.MODIFY,
                UnderwritingEnum.UnderwritingStatus.RATED, new BigDecimal("0.10"));
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    /**
     * 以给定输入与参数执行核保决策，逐项断言事件结论。
     * <p>
     * 断言走 {@code expectResultMessageMatching} 且文案自撰（含 expected/actual），失败可直接归因，
     * 不依赖框架失败文案形状（反向注入探针据此判定「失败正是被注入语义所在」）。
     * </p>
     *
     * @param input               核保输入
     * @param parameter           命令携带的核保风险参数
     * @param expectedLevel       期望风险等级
     * @param expectedConclusion  期望核保结论
     * @param expectedStatus      期望核保状态
     * @param expectedRatio       期望加费率（null = 期望无加费明细）
     */
    private void expectDecision(UnderwritingInput input, UnderwritingParameter parameter,
            UnderwritingEnum.RiskLevel expectedLevel, UnderwritingEnum.ConclusionType expectedConclusion,
            UnderwritingEnum.UnderwritingStatus expectedStatus, BigDecimal expectedRatio) {
        DecideUnderwritingCommand decide = new DecideUnderwritingCommand(UNDERWRITING_ID,
                UnderwritingEnum.AuditType.AUTOMATIC, "system", TENANT_ID, true, null,
                ProductConfigSource.CONFIGURED, parameter);

        fixture.given(createdEvent(), new UnderwritingInputSubmittedEvent(UNDERWRITING_ID, input,
                        LocalDateTime.now(), "system", TENANT_ID))
                .when(decide)
                .expectResultMessageMatching(messageWithPayload(matches((UnderwritingDecidedEvent event) -> {
                    assertEquals(expectedLevel, event.riskLevel(), "风险等级不符（参数覆盖未生效或阈值口径漂移）");
                    assertEquals(expectedConclusion, event.conclusionType());
                    assertEquals(expectedStatus, event.newStatus());
                    if (expectedRatio == null) {
                        assertNull(event.extraPremium(), "非次标准体不应产出加费明细");
                    } else {
                        assertEquals(0, expectedRatio.compareTo(event.extraPremium().ratio()),
                                "加费率不符：expected=" + expectedRatio + ", actual="
                                        + event.extraPremium().ratio());
                    }
                    return true;
                })));
    }

    /** 职业类别 4 → 内置评分 45（次标准体区间内）的输入 */
    private static UnderwritingInput occupationCategoryFourInput() {
        return UnderwritingInput.builder().insuredRiskFactors(new InsuredRiskFactors(null, null, 4, null)).build();
    }

    /** 1 项家族史、不吸烟、BMI 22.5 正常的健康告知输入 */
    private static UnderwritingInput oneFamilyHistoryInput() {
        return UnderwritingInput.builder()
                .healthDeclaration(new HealthDeclaration(List.of(), List.of("糖尿病家族史"), false,
                        new BigDecimal("170"), new BigDecimal("65")))
                .build();
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

    private UnderwritingCreatedEvent createdEvent() {
        return new UnderwritingCreatedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"), CustomerId.of("CUS-001"),
                UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, LocalDateTime.now(), "system", TENANT_ID, "PRD-001",
                "UW202401001", null);
    }
}
