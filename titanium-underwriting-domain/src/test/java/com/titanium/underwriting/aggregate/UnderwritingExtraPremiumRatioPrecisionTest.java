package com.titanium.underwriting.aggregate;

import static org.axonframework.test.matchers.Matchers.matches;
import static org.axonframework.test.matchers.Matchers.messageWithPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
import com.titanium.underwriting.valueobject.InsuredRiskFactors;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;
import com.titanium.underwriting.valueobject.UnderwritingInput;
import com.titanium.underwriting.valueobject.UnderwritingParameter;

/**
 * 次标准体加费率折算精度测试（DSN-07 / m25-09）
 * <p>
 * 🔴 判别点：加费率由「超出标准体基准的评分 × 每分费率」折算，全程必须 BigDecimal。
 * 断言用 {@link BigDecimal#equals(Object)}（<b>scale 敏感</b>）：BigDecimal 链路产出
 * {@code "0.30"}（scale 2）；若回退为 double 运算再 {@code BigDecimal.valueOf(...)}，
 * 同一数值将呈现为 {@code "0.3"}（scale 1）——两条路径数值相等而表示不同，唯 scale 敏感断言可分辨。
 * </p>
 * <p>
 * 可达域与封顶边界（结构性分析，防误判）：内置评分路径下加费仅在 SUB_STANDARD 区间 [30,60) 产出
 * （≥60 即 HIGH_RISK→POSTPONE，无加费），故 excess = 评分−30 最大 29、比率最大 0.58——
 * {@code deriveExtraPremium} 的 {@code .min(BigDecimal.ONE)} 封顶在内置路径<b>结构上不可达</b>，
 * 属防御性契约（评分口径若演进则生效）。本测试覆盖 excess=15 与 excess=25 两个判别点。
 * </p>
 */
class UnderwritingExtraPremiumRatioPrecisionTest {

    private static final UnderwritingId UNDERWRITING_ID = new UnderwritingId("UW-PREC-001");
    private static final String         TENANT_ID       = "TENANT-001";

    private FixtureConfiguration<Underwriting> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Underwriting.class);
        fixture.setReportIllegalStateChange(false);
    }

    /**
     * 职业类别 4 → 评分 (4−1)×15 = 45（次标准体）→ MODIFY，excess=15 → 加费率 "0.30"（scale 2）。
     */
    @Test
    void shouldDeriveScaleStableRatioForExcess15() {
        decideWith(new InsuredRiskFactors(null, null, 4, null), 45, new BigDecimal("0.30"));
    }

    /**
     * 年龄 40（+10）+ 职业类别 4（+45）= 55（次标准体）→ MODIFY，excess=25 → 加费率 "0.50"（scale 2）。
     */
    @Test
    void shouldDeriveScaleStableRatioForExcess25() {
        decideWith(new InsuredRiskFactors(40, null, 4, null), 55, new BigDecimal("0.50"));
    }

    private void decideWith(InsuredRiskFactors factors, int expectedRiskScore, BigDecimal expectedRatio) {
        UnderwritingInput input = UnderwritingInput.builder().insuredRiskFactors(factors).build();
        DecideUnderwritingCommand decide = new DecideUnderwritingCommand(UNDERWRITING_ID,
                UnderwritingEnum.AuditType.AUTOMATIC, "system", TENANT_ID, true, null,
                ProductConfigSource.CONFIGURED, UnderwritingParameter.DEFAULT);

        fixture.given(createdEvent(), new UnderwritingInputSubmittedEvent(UNDERWRITING_ID, input,
                        LocalDateTime.now(), "system", TENANT_ID))
                .when(decide)
                .expectResultMessageMatching(messageWithPayload(matches((UnderwritingDecidedEvent event) -> {
                    assertEquals(expectedRiskScore, event.riskScore(), "评分口径变动，用例前提失效");
                    assertEquals(UnderwritingEnum.RiskLevel.SUB_STANDARD, event.riskLevel());
                    assertEquals(UnderwritingEnum.ConclusionType.MODIFY, event.conclusionType());
                    assertEquals(expectedRatio, event.extraPremium().ratio(),
                            "加费率须为 BigDecimal 折算的 scale-2 表示；double 回退将得 scale-1 的 "
                                    + expectedRatio.stripTrailingZeros().toPlainString());
                    return true;
                })));
    }

    private UnderwritingCreatedEvent createdEvent() {
        return new UnderwritingCreatedEvent(UNDERWRITING_ID, PolicyId.of("POL-001"), CustomerId.of("CUS-001"),
                UnderwritingAmount.of(BigDecimal.ZERO, CurrencyEnum.CNY),
                UnderwritingEnum.UnderwritingType.NEW_BUSINESS, LocalDateTime.now(), "system", TENANT_ID, "PRD-001",
                "UW202401001", null);
    }
}
