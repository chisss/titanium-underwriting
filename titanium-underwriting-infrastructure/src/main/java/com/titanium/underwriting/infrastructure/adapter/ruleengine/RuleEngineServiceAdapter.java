package com.titanium.underwriting.infrastructure.adapter.ruleengine;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.RuleEngineErrorCode;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.ruleengine.api.response.RuleExecutionResultResponse;
import com.titanium.ruleengine.common.enums.RuleDecision;
import com.titanium.underwriting.port.ruleengine.RuleEngineServicePort;
import com.titanium.underwriting.valueobject.RuleExecutionResult;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则引擎执行适配器（driven adapter，位于 infrastructure，按对端域 ruleengine 拆子包）
 * <p>
 * 实现 {@link RuleEngineServicePort}，经 Feign {@link RuleEngineApi} 调用规则引擎域
 * {@code POST /api/v1/rules/execute/{ruleSetCode}}，把规则引擎枚举结论转换为核保域值对象
 * {@link RuleExecutionResult}（领域不依赖规则引擎类型）。
 * </p>
 * <p>
 * 错误映射（dev-505 / G13）：规则引擎返回的结构化错误按业务错误码还原——G13 变量缺失
 * （66000012）与底层 EL1008E 逃逸统一映射为 {@link UnderwritingErrorCode#RULE_CONTEXT_VARIABLE_MISSING}，
 * 其余失败映射 {@link UnderwritingErrorCode#RULE_ENGINE_EXECUTION_FAILED}，均抛出携带
 * {@code BaseErrorCode} 的 {@link BusinessException}，禁止裸字符串错误码。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineServiceAdapter implements RuleEngineServicePort {

    private final RuleEngineApi ruleEngineApi;

    @Override
    public RuleExecutionResult executeRuleSet(String tenantId, String ruleSetCode, Map<String, Object> context) {
        log.info("[规则引擎] 执行规则集: ruleSetCode={}, tenantId={}, variables={}", ruleSetCode, tenantId,
                context != null ? context.keySet() : null);
        try {
            ApiResponse<RuleExecutionResultResponse> response = ruleEngineApi.execute(ruleSetCode, context, tenantId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                String message = response != null ? response.getMessage() : "规则引擎无响应";
                log.error("[规则引擎] 规则集执行失败: ruleSetCode={}, error={}", ruleSetCode, message);
                throw new BusinessException(message, UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED);
            }
            return toResult(ruleSetCode, response.getData());
        } catch (BusinessException ex) {
            throw ex;
        } catch (FeignException ex) {
            throw mapFeignException(ruleSetCode, ex);
        } catch (Exception ex) {
            log.error("[规则引擎] 规则集执行异常: ruleSetCode={}, error={}", ruleSetCode, ex.getMessage(), ex);
            throw new BusinessException("规则引擎执行失败: " + ex.getMessage(),
                    UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED);
        }
    }

    /**
     * 规则引擎响应 → 核保域执行结果值对象。
     *
     * @param ruleSetCode 规则集编码（异常上下文）
     * @param data        规则引擎执行结果
     * @return 域内执行结果
     */
    private RuleExecutionResult toResult(String ruleSetCode, RuleExecutionResultResponse data) {
        RuleDecision decision = data.getDecision();
        if (decision == null) {
            throw new BusinessException("规则集执行结果缺少决策结论: " + ruleSetCode,
                    UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED);
        }
        Map<String, Object> actionParams = data.getOutputs() != null ? data.getOutputs() : Map.of();
        String reason = actionParams.get("reason") != null ? String.valueOf(actionParams.get("reason")) : null;
        // PASS 与 G16 新增的 SURCHARGE（加费承保）均视为放行，加费语义由领域结论映射区分
        boolean passed = decision == RuleDecision.PASS || decision == RuleDecision.SURCHARGE;
        return new RuleExecutionResult(passed, decision.name(), reason, actionParams);
    }

    /**
     * Feign 异常 → 业务异常（还原规则引擎结构化错误码，G13 变量缺失转可读错误）。
     * <p>
     * 规则引擎的 {@code RuleEngineExceptionHandler} 已把领域异常统一为 {@link ApiResponse} 业务错误码；
     * 此处解析响应体还原错误码：变量缺失（66000012）或底层 EL1008E 逃逸映射为
     * {@link UnderwritingErrorCode#RULE_CONTEXT_VARIABLE_MISSING}，其余映射执行失败。
     * </p>
     *
     * @param ruleSetCode 规则集编码
     * @param ex          Feign 异常
     * @return 业务异常
     */
    private BusinessException mapFeignException(String ruleSetCode, FeignException ex) {
        String code = null;
        String message = ex.getMessage();
        try {
            String body = ex.contentUTF8();
            if (body != null && !body.isBlank()) {
                JSONObject json = JSONObject.parseObject(body);
                code = json.getString("code");
                String bodyMessage = json.getString("message");
                if (bodyMessage != null && !bodyMessage.isBlank()) {
                    message = bodyMessage;
                }
            }
        } catch (Exception parseEx) {
            log.debug("[规则引擎] 解析 Feign 异常响应体失败: ruleSetCode={}, error={}", ruleSetCode, parseEx.getMessage());
        }
        boolean variableMissing = RuleEngineErrorCode.RULE_VARIABLE_MISSING.getCode().equals(code)
                || (message != null && message.contains("EL1008E"));
        UnderwritingErrorCode errorCode = variableMissing
                ? UnderwritingErrorCode.RULE_CONTEXT_VARIABLE_MISSING
                : UnderwritingErrorCode.RULE_ENGINE_EXECUTION_FAILED;
        log.error("[规则引擎] 规则集执行失败: ruleSetCode={}, code={}, error={}", ruleSetCode, code, message);
        return new BusinessException("规则集执行失败: " + message, errorCode);
    }
}
