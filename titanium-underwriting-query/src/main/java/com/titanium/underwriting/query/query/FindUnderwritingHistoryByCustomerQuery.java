package com.titanium.underwriting.query.query;

import com.titanium.underwriting.valueobject.CustomerId;

/**
 * 根据客户ID查询核保历史记录
 * 用于查询客户的所有核保记录，支持客户画像分析
 */
public record FindUnderwritingHistoryByCustomerQuery(
        CustomerId customerId,
        String tenantId
) {
}
