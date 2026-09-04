package com.titanium.underwriting.infrastructure.adapter.ruleengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.response.RuleExecutionResultResponse;
import com.titanium.ruleengine.common.enums.RuleDecision;
import com.titanium.underwriting.valueobject.RuleExecutionResult;

import feign.FeignException;

/**
 * 规则引擎执行适配器单元测试（dev-505）
 * <p>
 * 覆盖成功映射（PASS/SURCHARGE 均放行、原因透传）与三类失败路径：
 * 业务失败响应、G13 变量缺失（66000012 / EL1008E 逃逸）、决策缺失，均断言携带
 * {@code UnderwritingErrorCode} 结构化错误码。
 * </p>
 */
class RuleEngineServiceAdapterTest {

    private final RuleEngineApi api = mock(RuleEngineApi.class);

    private final RuleEngineServiceAdapter adapter = new RuleEngineServiceAdapter(api);

    @Test
    @DisplayName("PASS 结论 → 放行结果，原因与动作参数透传")
    void passDecisionMapsToPassedResult() {
        when(api.execute(eq("UW_STD_001"), anyMap(), anyString()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder()
                        .decision(RuleDecision.PASS)
                        .outputs(Map.of("reason", "标准体规则命中"))
                        .build()));

        RuleExecutionResult result = adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of("age", 30));

        assertTrue(result.passed());
        assertEquals("PASS", result.conclusion());
        assertEquals("标准体规则命中", result.reason());
        verify(api).execute("UW_STD_001", Map.of("age", 30), "TENANT-001");
    }

    @Test
    @DisplayName("SURCHARGE 结论 → 放行（加费语义由结论映射区分）")
    void surchargeDecisionMapsToPassedResult() {
        when(api.execute(anyString(), anyMap(), anyString()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder()
                        .decision(RuleDecision.SURCHARGE)
                        .outputs(Map.of("surchargeRate", 0.2))
                        .build()));

        RuleExecutionResult result = adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of());

        assertTrue(result.passed());
        assertEquals("SURCHARGE", result.conclusion());
        assertEquals(0.2, result.actionParams().get("surchargeRate"));
    }

    @Test
    @DisplayName("业务失败响应 → 抛 RULE_ENGINE_EXECUTION_FAILED")
    void businessFailureResponseThrowsExecutionFailed() {
        when(api.execute(anyString(), anyMap(), anyString()))
                .thenReturn(ApiResponse.error(com.titanium.metadata.errorcode.RuleEngineErrorCode.RULE_EXECUTE_FAILED,
                        "规则执行失败"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of()));

        assertEquals(UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("响应缺决策结论 → 抛 RULE_ENGINE_EXECUTION_FAILED")
    void responseWithoutDecisionThrowsExecutionFailed() {
        when(api.execute(anyString(), anyMap(), anyString()))
                .thenReturn(ApiResponse.success(RuleExecutionResultResponse.builder().build()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of()));

        assertEquals(UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Feign 异常携带 66000012 变量缺失 → 抛 RULE_CONTEXT_VARIABLE_MISSING")
    void feignVariableMissingCodeMapsToContextVariableMissing() {
        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("变量缺失");
        when(feignException.contentUTF8())
                .thenReturn("{\"code\":\"66000012\",\"message\":\"规则条件引用的输入变量未提供: bmiLevel\"}");
        when(api.execute(anyString(), anyMap(), anyString())).thenThrow(feignException);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of()));

        assertEquals(UnderwritingErrorCode.RULE_CONTEXT_VARIABLE_MISSING.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().contains("bmiLevel"));
    }

    @Test
    @DisplayName("Feign 异常消息含 EL1008E 逃逸 → 抛 RULE_CONTEXT_VARIABLE_MISSING")
    void feignEl1008EMessageMapsToContextVariableMissing() {
        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("EL1008E: Property or field 'bmiLevel' cannot be found");
        when(feignException.contentUTF8()).thenReturn(null);
        when(api.execute(anyString(), anyMap(), anyString())).thenThrow(feignException);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of()));

        assertEquals(UnderwritingErrorCode.RULE_CONTEXT_VARIABLE_MISSING.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("其余 Feign 异常 → 抛 RULE_ENGINE_EXECUTION_FAILED")
    void otherFeignExceptionMapsToExecutionFailed() {
        FeignException feignException = mock(FeignException.class);
        when(feignException.getMessage()).thenReturn("服务不可用");
        when(feignException.contentUTF8())
                .thenReturn("{\"code\":\"66000001\",\"message\":\"规则集不存在\"}");
        when(api.execute(anyString(), anyMap(), anyString())).thenThrow(feignException);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of()));

        assertEquals(UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("响应为 null → 抛 RULE_ENGINE_EXECUTION_FAILED")
    void nullResponseThrowsExecutionFailed() {
        when(api.execute(anyString(), anyMap(), anyString())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adapter.executeRuleSet("TENANT-001", "UW_STD_001", Map.of()));

        assertEquals(UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED.getCode(), ex.getErrorCode());
        assertFalse(ex.getMessage().isBlank());
    }
}
