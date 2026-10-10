package com.titanium.underwriting.valueobject;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

/**
 * 自动决策粗粒度请求（g02-04 / AC-05）
 * <p>
 * 一次调用完成「创建/幂等复用 + 提交输入 + 出具决策」所需的全部要素，取代此前由上游拼装的四步远程调用。
 * 位于 {@code domain/valueobject} 而非 {@code application}，遵根规约 §3.4.4「service 包禁放 record，
 * 入参/出参 record 一律入 valueobject」（同 policy 域 {@code IssuanceRequest}/{@code IssuanceResult} 先例）。
 * </p>
 * <p>
 * 🔴 {@code insuranceId} 是<b>幂等键</b>：同一投保单重复调用必须落到同一张核保单，不得重复建单。
 * 其中 {@code amount} 已由边界层装配为带币种校验的 {@link UnderwritingAmount}，
 * {@code riskFactors} 为粗粒度风险要素容器（年龄/性别/职业类别/BMI，四项可全空——由
 * {@link InsuredRiskFactors#hasAny()} 判定，业务上允许「无输入」，此时决策回退金额阈值规则）。
 * </p>
 *
 * @param insuranceId      投保单号（幂等键，必填）
 * @param customerId       客户ID（对应上游投保人 holderId）
 * @param amount           核保金额（含币种）
 * @param underwritingType 核保类型（新单/续保/保全/复效）
 * @param productCode      险种编码（供取产品核保配置，可为空）
 * @param riskFactors      被保人粗粒度风险要素（可为空表示未提供）
 * @param operatorId       操作人（由出单链路以系统主体上报，不得回落为投保人）
 * @param tenantId         租户ID
 * @author wei.sun
 * @since 2026/9/27
 */
public record AutoDecideRequest(InsuranceId insuranceId, CustomerId customerId, UnderwritingAmount amount,
                                UnderwritingEnum.UnderwritingType underwritingType, String productCode,
                                InsuredRiskFactors riskFactors, String operatorId, String tenantId) {
}
