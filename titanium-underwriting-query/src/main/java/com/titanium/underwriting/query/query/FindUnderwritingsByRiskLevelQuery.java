package com.titanium.underwriting.query.query;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import org.springframework.data.domain.Pageable;

/**
 * 根据风险等级查询核保信息列表
 * 支持按风险等级（标准体/次标准体/高风险体）查询核保记录
 */
public record FindUnderwritingsByRiskLevelQuery(
        UnderwritingEnum.RiskLevel riskLevel,
        Pageable pageable,
        String tenantId
) {
}