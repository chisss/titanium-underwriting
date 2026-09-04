package com.titanium.underwriting.service.impl;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.underwriting.common.constant.UnderwritingConstants;
import com.titanium.underwriting.service.RuleConclusionMappingService;
import com.titanium.underwriting.valueobject.ExtraPremium;
import com.titanium.underwriting.valueobject.RuleExecutionResult;
import com.titanium.underwriting.valueobject.RuleUnderwritingDecision;

import lombok.extern.slf4j.Slf4j;

/**
 * 规则引擎结论映射领域服务实现（dev-505 纯领域规则）
 * <p>
 * 结论映射规则（决策表）：
 * <ul>
 *   <li>PASS → 返回 null，回退聚合内置评分路径（规则放行不代表跳过风险评分）</li>
 *   <li>REFER → 转人工复核（MANUAL_REVIEW，无风险等级/结论）</li>
 *   <li>SURCHARGE → 加费承保（MODIFY/SUB_STANDARD/RATED，加费率取 actionParams.surchargeRate；
 *       未携带加费率时降级转人工复核，避免无法定价却盲目承保）</li>
 *   <li>REJECT → 携带加费率且产品允许加费时加费承保，否则拒保（REJECT/UNINSURABLE/DECLINED）</li>
 *   <li>未知结论 → 抛 {@link UnderwritingErrorCode#RULE_ENGINE_CONCLUSION_UNSUPPORTED}</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class RuleConclusionMappingServiceImpl implements RuleConclusionMappingService {

    @Override
    public RuleUnderwritingDecision map(RuleExecutionResult result, boolean surchargeAcceptable) {
        if (result == null || result.conclusion() == null) {
            throw new DomainException(UnderwritingErrorCode.RULE_ENGINE_CONCLUSION_UNSUPPORTED,
                    "规则引擎执行结果缺少决策结论");
        }
        return switch (result.conclusion()) {
            case UnderwritingConstants.RULE_CONCLUSION_PASS -> null;
            case UnderwritingConstants.RULE_CONCLUSION_REFER -> manualReviewDecision(result);
            case UnderwritingConstants.RULE_CONCLUSION_SURCHARGE -> surchargeDecision(result);
            case UnderwritingConstants.RULE_CONCLUSION_REJECT -> rejectDecision(result, surchargeAcceptable);
            default -> throw new DomainException(UnderwritingErrorCode.RULE_ENGINE_CONCLUSION_UNSUPPORTED,
                    String.format("规则引擎决策结论[%s]超出核保域支持范围", result.conclusion()));
        };
    }

    /**
     * 转人工复核决策：不产出风险等级与核保结论，仅状态 MANUAL_REVIEW + 规则原因。
     *
     * @param result 规则引擎执行结果
     * @return 转人工决策
     */
    private RuleUnderwritingDecision manualReviewDecision(RuleExecutionResult result) {
        return new RuleUnderwritingDecision(null, null, UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW, null,
                result.reason());
    }

    /**
     * 加费承保决策（SURCHARGE）：加费率缺失时降级转人工复核。
     *
     * @param result 规则引擎执行结果
     * @return 加费承保决策（或转人工降级决策）
     */
    private RuleUnderwritingDecision surchargeDecision(RuleExecutionResult result) {
        BigDecimal rate = surchargeRate(result);
        if (rate == null) {
            log.warn("[规则结论映射] SURCHARGE 结论未携带加费率，降级转人工复核: reason={}", result.reason());
            return manualReviewDecision(result);
        }
        ExtraPremium premium = ExtraPremium.ofRatio(rate, null, surchargeReason(result));
        return new RuleUnderwritingDecision(UnderwritingEnum.ConclusionType.MODIFY,
                UnderwritingEnum.RiskLevel.SUB_STANDARD, UnderwritingEnum.UnderwritingStatus.RATED, premium,
                result.reason());
    }

    /**
     * 拒绝决策（REJECT）：携带加费率且产品允许加费时转为加费承保，否则拒保。
     *
     * @param result              规则引擎执行结果
     * @param surchargeAcceptable 产品是否允许加费承保
     * @return 加费承保或拒保决策
     */
    private RuleUnderwritingDecision rejectDecision(RuleExecutionResult result, boolean surchargeAcceptable) {
        BigDecimal rate = surchargeRate(result);
        if (rate != null && surchargeAcceptable) {
            ExtraPremium premium = ExtraPremium.ofRatio(rate, null, surchargeReason(result));
            return new RuleUnderwritingDecision(UnderwritingEnum.ConclusionType.MODIFY,
                    UnderwritingEnum.RiskLevel.SUB_STANDARD, UnderwritingEnum.UnderwritingStatus.RATED, premium,
                    result.reason());
        }
        return new RuleUnderwritingDecision(UnderwritingEnum.ConclusionType.REJECT,
                UnderwritingEnum.RiskLevel.UNINSURABLE, UnderwritingEnum.UnderwritingStatus.DECLINED, null,
                result.reason());
    }

    /**
     * 解析规则动作参数中的加费率（容忍 Number 与数字字符串两种形态，非法值返回 null）。
     *
     * @param result 规则引擎执行结果
     * @return 加费率；缺失或非法时返回 null
     */
    private BigDecimal surchargeRate(RuleExecutionResult result) {
        Map<String, Object> actionParams = result.actionParams();
        if (actionParams == null) {
            return null;
        }
        Object raw = actionParams.get(UnderwritingConstants.RULE_ACTION_KEY_SURCHARGE_RATE);
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            log.warn("[规则结论映射] 加费率非法，按缺失处理: surchargeRate={}", raw);
            return null;
        }
    }

    /**
     * 加费原因文案：优先规则引擎给出的原因，缺失时按结论模板兜底（红线 20 文案常量化）。
     *
     * @param result 规则引擎执行结果
     * @return 加费原因
     */
    private String surchargeReason(RuleExecutionResult result) {
        if (result.reason() != null && !result.reason().isBlank()) {
            return result.reason();
        }
        return String.format(UnderwritingConstants.RULE_EXTRA_PREMIUM_REASON_TEMPLATE, result.conclusion());
    }
}
