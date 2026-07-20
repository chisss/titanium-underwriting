package com.titanium.underwriting.api.request;

import lombok.Data;

/**
 * 触发核保决策请求（Feign 跨域契约）
 * <p>
 * 基于已提交的结构化输入产出核保结论、风险等级、加费。供 policy 出单调用（富核保路径）。
 * </p>
 */
@Data
public class DecideUnderwritingApiRequest {

    /** 审核类型（自动/人工） */
    private String auditType;

    /** 决策人 */
    private String decidedBy;

    /** 险种编码（UW-4：供核保域应用层查询产品核保配置，如加费许可/阈值；可为空走默认配置） */
    private String productCode;
}
