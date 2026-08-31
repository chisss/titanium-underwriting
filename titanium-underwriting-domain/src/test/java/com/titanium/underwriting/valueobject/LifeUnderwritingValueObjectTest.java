package com.titanium.underwriting.valueobject;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.underwriting.ExtraPremiumType;
import com.titanium.underwriting.exception.UnderwritingValidationException;

/**
 * 寿险核保结构化值对象测试（P1.6 财务评估 + 加费）
 * <p>
 * 覆盖 {@link FinancialAssessment} 财务风险评分与人工财务核保判定、{@link ExtraPremium} 加费计算，
 * 以及 {@link UnderwritingInput} 将财务评估纳入综合风险评分的短板优先规则。
 * </p>
 */
class LifeUnderwritingValueObjectTest {

    // ---- FinancialAssessment ----

    @Test
    @DisplayName("保额为年收入10倍以内财务风险低")
    void financialLowRiskWithinIncomeMultiple() {
        FinancialAssessment fa = new FinancialAssessment(new BigDecimal("200000"), new BigDecimal("1000000"),
                new BigDecimal("1000000"), "工资");
        // 保额100万/年收入20万=5倍<10倍，净资产覆盖，风险应为0
        if (fa.riskScore() != 0) {
            throw new AssertionError("保额5倍且净资产覆盖，财务风险应为0，实际=" + fa.riskScore());
        }
        if (fa.requiresManualFinancialReview()) {
            throw new AssertionError("5倍保额不应触发人工财务核保");
        }
    }

    @Test
    @DisplayName("保额超年收入20倍触发人工财务核保且高分")
    void financialHighRiskAboveTwentyMultiple() {
        FinancialAssessment fa = new FinancialAssessment(new BigDecimal("100000"), new BigDecimal("5000000"),
                new BigDecimal("3000000"), "工资");
        // 保额300万/年收入10万=30倍>=20倍，触发人工核保，+60分
        if (!fa.requiresManualFinancialReview()) {
            throw new AssertionError("30倍保额应触发人工财务核保");
        }
        if (fa.riskScore() < 60) {
            throw new AssertionError("30倍保额财务风险应>=60，实际=" + fa.riskScore());
        }
    }

    @Test
    @DisplayName("申请保额超净资产追加风险分")
    void financialRiskWhenExceedNetWorth() {
        FinancialAssessment fa = new FinancialAssessment(new BigDecimal("500000"), new BigDecimal("1000000"),
                new BigDecimal("6000000"), "经营");
        // 保额600万/年收入50万=12倍(>=10<20 → +30)，且保额>净资产100万(+20)=50
        if (fa.riskScore() != 50) {
            throw new AssertionError("12倍且超净资产财务风险应为50，实际=" + fa.riskScore());
        }
    }

    // ---- ExtraPremium ----

    @Test
    @DisplayName("比例加费按标准保费百分比计算")
    void ratioExtraPremiumApply() {
        ExtraPremium ep = ExtraPremium.ofRatio(new BigDecimal("0.5"), null, "既往病史");
        BigDecimal result = ep.applyTo(new BigDecimal("1000"));
        // 1000 ×(1+0.5)=1500
        if (result.compareTo(new BigDecimal("1500.0")) != 0) {
            throw new AssertionError("50%加费后保费应为1500，实际=" + result);
        }
        if (ep.type() != ExtraPremiumType.PERMANENT_RATIO) {
            throw new AssertionError("无期限比例加费应为永久费率加费");
        }
    }

    @Test
    @DisplayName("临时比例加费带期限")
    void temporaryRatioExtraPremium() {
        ExtraPremium ep = ExtraPremium.ofRatio(new BigDecimal("0.3"), 5, "临时加费");
        if (!ep.isTemporary() || ep.type() != ExtraPremiumType.TEMPORARY_RATIO) {
            throw new AssertionError("带期限比例加费应为临时费率加费且 isTemporary");
        }
    }

    @Test
    @DisplayName("定额加费在标准保费上追加固定额")
    void fixedExtraPremiumApply() {
        ExtraPremium ep = ExtraPremium.ofFixed(new BigDecimal("500"), null, "特定风险");
        BigDecimal result = ep.applyTo(new BigDecimal("1000"));
        if (result.compareTo(new BigDecimal("1500")) != 0) {
            throw new AssertionError("定额加费500后保费应为1500，实际=" + result);
        }
    }

    @Test
    @DisplayName("比例加费率非正数被拒绝")
    void ratioMustBePositive() {
        try {
            ExtraPremium.ofRatio(BigDecimal.ZERO, null, "非法");
            throw new AssertionError("零加费率应抛异常");
        } catch (UnderwritingValidationException expected) {
            // 预期
        }
    }

    // ---- UnderwritingInput 综合 ----

    @Test
    @DisplayName("财务评估纳入综合风险评分（短板优先取最大）")
    void inputAggregatesFinancialRisk() {
        FinancialAssessment fa = new FinancialAssessment(new BigDecimal("100000"), new BigDecimal("5000000"),
                new BigDecimal("3000000"), "工资");
        UnderwritingInput input = UnderwritingInput.builder().financialAssessment(fa).build();
        if (!input.hasAnyInput()) {
            throw new AssertionError("含财务评估应视为有输入");
        }
        if (input.aggregateRiskScore() < 60) {
            throw new AssertionError("财务高风险应拉高综合评分，实际=" + input.aggregateRiskScore());
        }
        if (!input.requiresManualFinancialReview()) {
            throw new AssertionError("应透传财务人工核保标记");
        }
    }
}
