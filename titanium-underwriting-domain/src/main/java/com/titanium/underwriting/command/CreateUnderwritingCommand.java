package com.titanium.underwriting.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 创建核保命令
 * <p>
 * {@code productCode} 为可选险种编码，用于 application 层查询产品核保配置（UW-4 因子配置化）。
 * 为 null 时决策走默认配置（允许加费，无金额限制）。
 * </p>
 * <p>
 * {@code caseNo} 为核保案号（UW 前缀业务号），由 application 层调用
 * {@code UnderwritingNoGenerator} 生成后回填，上游调用方不传。
 * </p>
 */
public record CreateUnderwritingCommand(@TargetAggregateIdentifier UnderwritingId underwritingId, PolicyId policyId,
                                        CustomerId customerId, UnderwritingAmount amount,
                                        UnderwritingEnum.UnderwritingType underwritingType,
                                        String createdBy, String tenantId,
                                        String productCode, String caseNo) {
}
