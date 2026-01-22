package com.titanium.underwriting.domain.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.domain.aggregate.Underwriting;
import com.titanium.underwriting.domain.command.UnderwriteCommand;
import com.titanium.underwriting.common.exception.UnderwritingException;

/**
 * 核保领域服务
 */
@Service
public class UnderwritingDomainService {

    /**
     * 自动核保规则 根据风险评估结果确定核保状态
     */
    public UnderwritingEnum.UnderwritingStatus determineUnderwritingStatus(UnderwriteCommand command,
                                                                           Underwriting underwriting) {
        // 1. 检查基本条件
        validateUnderwritingConditions(command, underwriting);

        // 2. 执行风险评估
        RiskAssessmentResult riskAssessment = performRiskAssessment(command);

        // 3. 根据风险评分确定核保状态
        switch (riskAssessment.getRiskLevel()) {
            case LOW:
                return UnderwritingEnum.UnderwritingStatus.APPROVED;
            case MEDIUM:
                return UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW;
            case HIGH:
                return UnderwritingEnum.UnderwritingStatus.REJECTED;
            default:
                throw new UnderwritingException("UNDERWRITING_RISK_LEVEL_ERROR", "未知的风险等级");
        }
    }

    /**
     * 验证核保基本条件
     */
    private void validateUnderwritingConditions(UnderwriteCommand command, Underwriting underwriting) {
        // 检查核保请求是否已过期
        if (underwriting.getCreatedAt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new UnderwritingException("UNDERWRITING_REQUEST_EXPIRED", "核保请求已过期");
        }

        // 检查当前核保状态是否为待核保
        if (!UnderwritingEnum.UnderwritingStatus.PENDING.equals(underwriting.getStatus())) {
            throw new UnderwritingException("UNDERWRITING_STATUS_INVALID", "当前核保状态不允许执行核保操作");
        }

        // 检查核保金额是否有效
        if (command.amount().getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnderwritingException("UNDERWRITING_AMOUNT_INVALID", "核保金额无效");
        }
    }

    /**
     * 执行风险评估
     */
    private RiskAssessmentResult performRiskAssessment(UnderwriteCommand command) {
        // 这里实现风险评估逻辑
        // 例如：基于客户信息、投保金额、险种类型等因素计算风险评分
        int riskScore = calculateRiskScore(command);

        // 根据风险评分确定风险等级
        RiskLevel riskLevel;
        if (riskScore < 30) {
            riskLevel = RiskLevel.LOW;
        } else if (riskScore < 70) {
            riskLevel = RiskLevel.MEDIUM;
        } else {
            riskLevel = RiskLevel.HIGH;
        }

        return new RiskAssessmentResult(riskScore, riskLevel);
    }

    /**
     * 计算风险评分
     */
    private int calculateRiskScore(UnderwriteCommand command) {
        // 这里是风险评分的具体计算逻辑
        // 示例：根据投保金额、年龄、健康状况等因素进行评分
        int score = 0;

        // 投保金额评分
        BigDecimal amount = command.amount().getAmount();
        if (amount.compareTo(new BigDecimal(100000)) > 0) {
            score += 20;
        } else if (amount.compareTo(new BigDecimal(50000)) > 0) {
            score += 10;
        }

        // 其他评分逻辑...

        return score;
    }

    /**
     * 风险评估结果
     */
    private static class RiskAssessmentResult {
        private final int       riskScore;
        private final RiskLevel riskLevel;

        public RiskAssessmentResult(int riskScore, RiskLevel riskLevel) {
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
        }

        public int getRiskScore() {
            return riskScore;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }
    }

    /**
     * 风险等级
     */
    private enum RiskLevel {
        LOW, // 低风险
        MEDIUM, // 中等风险
        HIGH // 高风险
    }

    /**
     * 手动审核结果处理
     */
    public UnderwritingEnum.UnderwritingStatus processManualReviewResult(String reviewResult, String reviewReason) {
        // 验证审核结果
        if (reviewResult == null || reviewResult.isEmpty()) {
            throw new UnderwritingException("MANUAL_REVIEW_RESULT_INVALID", "审核结果不能为空");
        }

        // 根据审核结果返回相应的核保状态
        if ("APPROVE".equals(reviewResult)) {
            return UnderwritingEnum.UnderwritingStatus.APPROVED;
        } else if ("REJECT".equals(reviewResult)) {
            return UnderwritingEnum.UnderwritingStatus.REJECTED;
        } else {
            throw new UnderwritingException("MANUAL_REVIEW_RESULT_ERROR", "无效的审核结果");
        }
    }

    /**
     * 检查核保是否符合自动核保条件
     */
    public boolean isEligibleForAutoUnderwriting(UnderwriteCommand command) {
        // 例如：只有当投保金额低于一定阈值时才允许自动核保
        return command.amount().getAmount().compareTo(new BigDecimal(100000)) <= 0;
    }
}
