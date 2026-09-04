package com.titanium.underwriting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.underwriting.common.constant.UnderwritingConstants;
import com.titanium.underwriting.service.impl.RuleConclusionMappingServiceImpl;
import com.titanium.underwriting.valueobject.RuleExecutionResult;
import com.titanium.underwriting.valueobject.RuleUnderwritingDecision;

/**
 * 规则结论映射领域服务单元测试（dev-505 决策表全覆盖）
 * <p>
 * 覆盖 PASS/REFER/SURCHARGE/REJECT 四类结论映射与边界输入：
 * SURCHARGE 缺加费率降级转人工、REJECT 携带加费率且产品允许加费时加费承保、
 * 未知结论与空结果抛结构化异常。
 * </p>
 */
class RuleConclusionMappingServiceTest {

    private final RuleConclusionMappingService service = new RuleConclusionMappingServiceImpl();

    @Test
    @DisplayName("PASS → 返回 null，回退聚合内置评分路径")
    void passReturnsNullForBuiltInScoring() {
        RuleExecutionResult result = new RuleExecutionResult(true, UnderwritingConstants.RULE_CONCLUSION_PASS,
                "标准体通过", null);

        assertNull(service.map(result, false));
    }

    @Test
    @DisplayName("REFER → 转人工复核（无风险等级/结论，原因随决策传递）")
    void referMapsToManualReview() {
        RuleExecutionResult result = new RuleExecutionResult(false, UnderwritingConstants.RULE_CONCLUSION_REFER,
                "健康告知异常，需人工复核", null);

        RuleUnderwritingDecision decision = service.map(result, true);

        assertNull(decision.riskLevel());
        assertNull(decision.conclusionType());
        assertEquals(UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW, decision.newStatus());
        assertNull(decision.extraPremium());
        assertEquals("健康告知异常，需人工复核", decision.reason());
    }

    @Test
    @DisplayName("SURCHARGE → 加费承保（MODIFY/SUB_STANDARD/RATED + 加费率）")
    void surchargeMapsToRatedWithExtraPremium() {
        RuleExecutionResult result = new RuleExecutionResult(true, UnderwritingConstants.RULE_CONCLUSION_SURCHARGE,
                "轻度高血压加费 20%", Map.of(UnderwritingConstants.RULE_ACTION_KEY_SURCHARGE_RATE, 0.2));

        RuleUnderwritingDecision decision = service.map(result, false);

        assertEquals(UnderwritingEnum.ConclusionType.MODIFY, decision.conclusionType());
        assertEquals(UnderwritingEnum.RiskLevel.SUB_STANDARD, decision.riskLevel());
        assertEquals(UnderwritingEnum.UnderwritingStatus.RATED, decision.newStatus());
        assertNotNull(decision.extraPremium());
        assertEquals(0, new BigDecimal("0.2").compareTo(decision.extraPremium().ratio()));
        assertEquals("轻度高血压加费 20%", decision.extraPremium().reason());
    }

    @Test
    @DisplayName("SURCHARGE 缺加费率 → 降级转人工复核（无法定价不盲目承保）")
    void surchargeWithoutRateDegradesToManualReview() {
        RuleExecutionResult result = new RuleExecutionResult(true, UnderwritingConstants.RULE_CONCLUSION_SURCHARGE,
                "需加费但未给出加费率", null);

        RuleUnderwritingDecision decision = service.map(result, false);

        assertEquals(UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW, decision.newStatus());
        assertNull(decision.extraPremium());
    }

    @Test
    @DisplayName("REJECT + 加费率 + 产品允许加费 → 加费承保")
    void rejectWithRateAndSurchargeAcceptableMapsToRated() {
        RuleExecutionResult result = new RuleExecutionResult(false, UnderwritingConstants.RULE_CONCLUSION_REJECT,
                "风险偏高，加费 30% 可承保", Map.of(UnderwritingConstants.RULE_ACTION_KEY_SURCHARGE_RATE, "0.3"));

        RuleUnderwritingDecision decision = service.map(result, true);

        assertEquals(UnderwritingEnum.ConclusionType.MODIFY, decision.conclusionType());
        assertEquals(UnderwritingEnum.UnderwritingStatus.RATED, decision.newStatus());
        assertEquals(0, new BigDecimal("0.3").compareTo(decision.extraPremium().ratio()));
    }

    @Test
    @DisplayName("REJECT + 加费率 + 产品不允许加费 → 拒保（DECLINED）")
    void rejectWithRateButSurchargeNotAcceptableMapsToDeclined() {
        RuleExecutionResult result = new RuleExecutionResult(false, UnderwritingConstants.RULE_CONCLUSION_REJECT,
                "风险过高，拒保", Map.of(UnderwritingConstants.RULE_ACTION_KEY_SURCHARGE_RATE, 0.3));

        RuleUnderwritingDecision decision = service.map(result, false);

        assertEquals(UnderwritingEnum.ConclusionType.REJECT, decision.conclusionType());
        assertEquals(UnderwritingEnum.RiskLevel.UNINSURABLE, decision.riskLevel());
        assertEquals(UnderwritingEnum.UnderwritingStatus.DECLINED, decision.newStatus());
        assertNull(decision.extraPremium());
        assertEquals("风险过高，拒保", decision.reason());
    }

    @Test
    @DisplayName("REJECT 无加费率 → 拒保（DECLINED）")
    void rejectWithoutRateMapsToDeclined() {
        RuleExecutionResult result = new RuleExecutionResult(false, UnderwritingConstants.RULE_CONCLUSION_REJECT,
                "拒保", null);

        RuleUnderwritingDecision decision = service.map(result, false);

        assertEquals(UnderwritingEnum.ConclusionType.REJECT, decision.conclusionType());
        assertEquals(UnderwritingEnum.UnderwritingStatus.DECLINED, decision.newStatus());
        assertNull(decision.extraPremium());
    }

    @Test
    @DisplayName("未知结论 → 抛 RULE_ENGINE_CONCLUSION_UNSUPPORTED")
    void unknownConclusionThrowsDomainException() {
        RuleExecutionResult result = new RuleExecutionResult(false, "UNKNOWN", null, null);

        DomainException ex = assertThrows(DomainException.class, () -> service.map(result, true));

        assertEquals(UnderwritingErrorCode.RULE_ENGINE_CONCLUSION_UNSUPPORTED.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("空结果 → 抛 RULE_ENGINE_CONCLUSION_UNSUPPORTED")
    void nullResultThrowsDomainException() {
        DomainException ex = assertThrows(DomainException.class, () -> service.map(null, true));

        assertEquals(UnderwritingErrorCode.RULE_ENGINE_CONCLUSION_UNSUPPORTED.getCode(), ex.getErrorCode());
    }
}
