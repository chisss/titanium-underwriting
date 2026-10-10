package com.titanium.underwriting.query.query;

import com.titanium.underwriting.valueobject.InsuranceId;

/**
 * 根据投保单号查询核保单（g02-04）
 * <p>
 * 读侧幂等查询入参：自动决策端点按投保单号判「是否已有核保单」，据此复用/续跑/新建。
 * 与 {@link FindUnderwritingByPolicyIdQuery} 的区别是维度不同——前者按**真保单号**，本查询按**投保单号**；
 * 二者不可互换（详见迁移脚本 {@code underwriting_view_202609271200_weisun_ddl.sql} 的成因注释）。
 * </p>
 */
public record FindUnderwritingByInsuranceIdQuery(InsuranceId insuranceId, String tenantId) {
}
