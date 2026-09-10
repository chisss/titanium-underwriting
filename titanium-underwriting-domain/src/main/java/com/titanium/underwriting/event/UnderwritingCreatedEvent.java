package com.titanium.underwriting.event;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.CustomerId;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingAmount;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保创建事件
 * <p>
 * {@code productCode} 为可选险种编码，供读模型投影存储，application 层据此查询产品核保配置。
 * {@code caseNo} 为核保案号（UW 前缀业务号），与保单号 POL/投保单号 INS 同源发号体系。
 * </p>
 */
public record UnderwritingCreatedEvent(UnderwritingId underwritingId, PolicyId policyId, CustomerId customerId,
                                       UnderwritingAmount amount, UnderwritingEnum.UnderwritingType underwritingType,
                                       LocalDateTime createdAt, String createdBy, String tenantId,
                                       String productCode, String caseNo) {
}
