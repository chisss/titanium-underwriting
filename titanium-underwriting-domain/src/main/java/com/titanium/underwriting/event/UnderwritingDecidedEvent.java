package com.titanium.underwriting.event;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.valueobject.PolicyId;
import com.titanium.underwriting.valueobject.UnderwritingId;

/**
 * 核保决策已完成事件
 * <p>
 * 当核保聚合根基于险种专属输入完成决策时发布，携带核保结论
 * （{@link UnderwritingEnum.ConclusionType}）、风险等级（{@link UnderwritingEnum.RiskLevel}）
 * 及由结论映射出的核保状态变更。携带 {@code policyId}（投保单/保单关联键）供跨域异步回流 policy 域。
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
        LocalDateTime decidedAt,
        String decidedBy,
        String tenantId
) {
}
