package com.titanium.underwriting.event;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.ExtraPremium;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保决策已完成事件
 * <p>
 * 当核保聚合根基于险种专属输入完成决策时发布，携带核保结论
 * （{@link UnderwritingEnum.ConclusionType}）、风险等级（{@link UnderwritingEnum.RiskLevel}）
 * 及由结论映射出的核保状态变更。携带 {@code policyId}（投保单/保单关联键）供跨域异步回流 policy 域。
 * 次标准体修改条件承保时携带 {@code extraPremium} 加费明细（供 billing 域计算实收保费），
 * 标准体/拒保时为 null（Axon 事件加字段向后兼容既有事件流反序列化，缺字段兜底 null）。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public record UnderwritingDecidedEvent(
        UnderwritingId underwritingId,
        PolicyId policyId,
        UnderwritingEnum.RiskLevel riskLevel,
        UnderwritingEnum.ConclusionType conclusionType,
        UnderwritingEnum.AuditType auditType,
        UnderwritingEnum.UnderwritingStatus oldStatus,
        UnderwritingEnum.UnderwritingStatus newStatus,
        int riskScore,
        ExtraPremium extraPremium,
        LocalDateTime decidedAt,
        String decidedBy,
        String tenantId
) {
}
