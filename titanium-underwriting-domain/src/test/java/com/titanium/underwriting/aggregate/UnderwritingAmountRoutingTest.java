package com.titanium.underwriting.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.command.UnderwriteCommand;
import com.titanium.underwriting.event.UnderwritingCreatedEvent;
import com.titanium.underwriting.event.UnderwritingInputSubmittedEvent;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.InsuredRiskFactors;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;

/**
 * 核保金额路由判定测试（g02-02 / AC-03：人工复核阈值权威侧为产品配置）
 * <p>
 * 覆盖三组语义，均以「命令携带的产品阈值」为自变量：
 * <ol>
 *   <li><b>产品已配置阈值</b>：保额 &gt; 阈值转人工复核；保额 &lt; 阈值不转；<b>保额 == 阈值不转</b>
 *       （锁死 {@code >} 严格大于口径）；产品阈值<b>低于</b>兜底默认值时同样以产品值为准
 *       （唯一能区分「真读了产品配置」与「恒用兜底常量」的判别式）；</li>
 *   <li><b>产品未配置阈值</b>（命令携带 null）：回退兜底默认阈值，语义为「配置缺失不放松风控」，
 *       边界同样保持 {@code >}；</li>
 *   <li><b>已提交险种输入优先</b>：一旦有险种专属输入，金额阈值规则整条不参与判定——
 *       本组用例同时证明「阈值换成产品配置」没有扰动输入优先分支。</li>
 * </ol>
 * </p>
 */
class UnderwritingAmountRoutingTest {

    private static final UnderwritingId UNDERWRITING_ID    = new UnderwritingId("UW-001");
    private static final String         TENANT_ID          = "TENANT-001";
    /** 产品核保配置给出的阈值（50 万，G02 文档 AC-03 用例值） */
    private static final BigDecimal     PRODUCT_THRESHOLD   = BigDecimal.valueOf(500_000);
    /** 兜底默认阈值（10 万）——产品未配置时的回退值，用例内以字面量独立表达，不复用被测方常量 */
    private static final BigDecimal     FALLBACK_THRESHOLD  = BigDecimal.valueOf(100_000);

    private FixtureConfiguration<Underwriting> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Underwriting.class);
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    @DisplayName("产品阈值 50 万 + 保额 60 万 → 转人工复核（REVIEW）")
    void sumInsuredAboveProductThresholdIsRoutedToReview() {
        expectStatus(BigDecimal.valueOf(600_000), PRODUCT_THRESHOLD,
                UnderwritingEnum.UnderwritingStatus.REVIEW);
    }

    @Test
    @DisplayName("产品阈值 50 万 + 保额 10 万 → 不转人工（APPROVED）")
    void sumInsuredBelowProductThresholdIsNotRoutedToReview() {
        expectStatus(BigDecimal.valueOf(100_000), PRODUCT_THRESHOLD,
                UnderwritingEnum.UnderwritingStatus.APPROVED);
    }

    @Test
    @DisplayName("保额恰等于产品阈值 → 不转人工（判定为严格大于）")
    void sumInsuredEqualToProductThresholdIsNotRoutedToReview() {
        expectStatus(PRODUCT_THRESHOLD, PRODUCT_THRESHOLD, UnderwritingEnum.UnderwritingStatus.APPROVED);
    }

    @Test
    @DisplayName("产品阈值低于兜底默认值 → 仍按产品阈值判定（证明产品配置是权威，不是换个常量的巧合）")
    void productThresholdLowerThanFallbackIsHonored() {
        // 产品阈值 5 万 + 保额 8 万：若误用兜底默认（10 万）会判 APPROVED，只有真读产品值才判 REVIEW。
        // 本用例是「阈值确实来自产品配置」的唯一直接判别式——其余用例的产品阈值均高于兜底值，
        // 丢弃产品值后结论不变，无法区分「读到了产品配置」与「恒用兜底常量」。
        expectStatus(BigDecimal.valueOf(80_000), BigDecimal.valueOf(50_000),
                UnderwritingEnum.UnderwritingStatus.REVIEW);
    }

    @Test
    @DisplayName("产品未配置阈值 + 保额 20 万 → 回退兜底默认阈值并转人工（不放松风控）")
    void missingProductThresholdFallsBackToLegacyDefault() {
        expectStatus(BigDecimal.valueOf(200_000), null, UnderwritingEnum.UnderwritingStatus.REVIEW);
    }

    @Test
    @DisplayName("产品未配置阈值 + 保额 10 万 → 回退兜底默认阈值且边界不转人工")
    void missingProductThresholdKeepsLegacyBoundary() {
        expectStatus(FALLBACK_THRESHOLD, null, UnderwritingEnum.UnderwritingStatus.APPROVED);
    }

    @Test
    @DisplayName("已有险种输入时金额阈值整条不参与判定（输入优先分支未被阈值改造扰动）")
    void submittedRiskInputTakesPrecedenceOverAmountThreshold() {
        UnderwritingInput input = UnderwritingInput.builder()
                .insuredRiskFactors(new InsuredRiskFactors(30, null, null, null))
                .build();

        // 保额 100 万远超产品阈值，但已有输入 ⇒ 判定走风险等级路径（标准体 → STANDARD 状态）
        fixture.given(createdEvent(), new UnderwritingInputSubmittedEvent(UNDERWRITING_ID, input,
                        LocalDateTime.now(), "system", TENANT_ID))
                .when(underwriteCommand(BigDecimal.valueOf(1_000_000), PRODUCT_THRESHOLD))
                .expectSuccessfulHandlerExecution()
                .expectState(state -> assertEquals(UnderwritingEnum.UnderwritingStatus.STANDARD, state.getStatus(),
                        "金额路由结论不符（已有输入时应由风险等级决定）: sumInsured=1000000, expected=STANDARD, actual="
                                + state.getStatus()));
    }

    /**
     * 以给定保额与产品阈值执行核保，断言聚合最终状态。
     * <p>
     * 🔴 断言走 {@code expectState} 而<b>非</b> Axon 的 {@code expectResultMessageMatching}：
     * 断言文案由本测试自撰（含 sumInsured / threshold / expected / actual 四项），失败可直接归因，
     * 不依赖框架失败文案的形状（反向注入探针据此判定「失败正是被注入语义所在」）。
     * </p>
     *
     * @param sumInsured   保额
     * @param threshold    命令携带的产品阈值（null = 产品未配置）
     * @param expected     期望的核保状态
     */
    private void expectStatus(BigDecimal sumInsured, BigDecimal threshold,
                              UnderwritingEnum.UnderwritingStatus expected) {
        fixture.given(createdEvent())
                .when(underwriteCommand(sumInsured, threshold))
                .expectSuccessfulHandlerExecution()
                .expectState(state -> assertEquals(expected, state.getStatus(),
                        "金额路由结论不符: sumInsured=" + sumInsured + ", threshold=" + threshold
                                + ", expected=" + expected + ", actual=" + state.getStatus()));
    }

    private UnderwriteCommand underwriteCommand(BigDecimal sumInsured, BigDecimal threshold) {
        return new UnderwriteCommand(UNDERWRITING_ID, UnderwritingAmount.of(sumInsured, CurrencyEnum.CNY),
                "自动核保", "system", TENANT_ID, threshold);
    }

    private UnderwritingCreatedEvent createdEvent() {
        return new UnderwritingCreatedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"), CustomerId.of("CUS-001"),
                UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, LocalDateTime.now(), "system", TENANT_ID, "PRD-001",
                "UW202401001", null);
    }
}
