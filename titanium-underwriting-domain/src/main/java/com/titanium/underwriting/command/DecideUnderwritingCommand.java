package com.titanium.underwriting.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保决策命令
 * <p>
 * 基于已提交的险种专属输入与核保金额，产出核保结论（{@link UnderwritingEnum.ConclusionType}）
 * 与风险等级（{@link UnderwritingEnum.RiskLevel}）。决策逻辑内聚于核保聚合根，
 * 替代原"金额>100000硬编码"的简单规则。
 * </p>
 * <p>
 * {@code auditType} 指定核保方式（自动/人工/混合），供后续审计追溯。
 * {@code surchargeAcceptable} 来自产品核保配置（UW-4 因子配置化）：为 {@code false} 时，
 * 即使风险评分在次标准体区间，聚合根也不产出加费（直接拒保或降级处理）；
 * 为 {@code null} 时采用默认行为（允许加费，与原逻辑一致）。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public record DecideUnderwritingCommand(
        @TargetAggregateIdentifier UnderwritingId underwritingId,
        UnderwritingEnum.AuditType auditType,
        String decidedBy,
        String tenantId,
        Boolean surchargeAcceptable
) {
}
