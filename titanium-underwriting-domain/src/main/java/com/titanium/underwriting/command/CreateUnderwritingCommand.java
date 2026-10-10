package com.titanium.underwriting.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.InsuranceId;
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
 * <p>
 * 🔴 {@code insuranceId}（g02-04 新增，尾部追加）承载**投保单号**语义，是自动决策幂等键的权威来源；
 * 为 null 表示调用方未提供该维度（如 web 直连建单路径）。与 {@code policyId} **并存**而非替代——
 * 后者保留并继续填充（存量事件流与读模型列均按旧语义，详见 {@link InsuranceId} 类注释）。
 * </p>
 */
public record CreateUnderwritingCommand(@TargetAggregateIdentifier UnderwritingId underwritingId, PolicyId policyId,
                                        CustomerId customerId, UnderwritingAmount amount,
                                        UnderwritingEnum.UnderwritingType underwritingType,
                                        String createdBy, String tenantId,
                                        String productCode, String caseNo, InsuranceId insuranceId) {
}
