package com.titanium.underwriting.application.orchestration.assembler;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.underwriting.event.UnderwritingDecidedEvent;
import com.titanium.underwriting.query.result.UnderwritingQueryResult;
import com.titanium.underwriting.valueobject.AutoDecideResult;
import com.titanium.underwriting.valueobject.ExtraPremium;

/**
 * 自动决策结果装配器（g02-04）
 * <p>
 * 把两条来源不同的通道收敛为同一形状 {@link AutoDecideResult}：
 * <ul>
 *   <li><b>决策事件通道</b>（本次刚出具结论，强一致）：{@link #toResult(UnderwritingDecidedEvent)}；</li>
 *   <li><b>读模型通道</b>（幂等命中既有结论，最终一致）：{@link #toResult(UnderwritingQueryResult)}。</li>
 * </ul>
 * 两条通道返回同形结果，是本端点「新建」与「复用」对调用方不可区分的前提。
 * </p>
 * <p>
 * 🔴 <b>为何独立成类</b>：一次装配涉及 15 个字段的取值与分派（含「单一 reason → 三列」的状态分派），
 * 属红线 21「&gt;5 字段的对象组装必须抽象 Assembler/Builder」，不得内联进编排器业务方法。
 * </p>
 */
@Component
public class AutoDecisionAssembler {

    /**
     * 决策事件 → 自动决策结果
     * <p>
     * 🔴 <b>投保单号双读</b>：字段 {@code insuranceId} 为 g02-04 尾部追加，存量事件反序列化时为 null；
     * 此时回退 {@code policyId}（存量出单链路把投保单号装在 {@code policyId} 里，见迁移脚本成因注释），
     * 与 Kafka 消费端同一兜底口径。**无需回放事件流**。
     * </p>
     */
    public AutoDecideResult toResult(UnderwritingDecidedEvent event) {
        UnderwritingEnum.UnderwritingStatus status = event.newStatus();
        ExtraPremium extraPremium = event.extraPremium();
        return new AutoDecideResult(
                event.underwritingId() != null ? event.underwritingId().value() : null,
                resolveInsuranceId(event),
                status,
                event.conclusionType(),
                event.riskLevel(),
                event.riskScore(),
                extraPremium != null && extraPremium.type() != null ? extraPremium.type().getCode() : null,
                extraPremium != null ? extraPremium.ratio() : null,
                extraPremium != null ? extraPremium.fixedAmount() : null,
                extraPremium != null ? extraPremium.reason() : null,
                isRejected(status) ? event.reason() : null,
                isManualReview(status) ? event.reason() : null,
                isExcluded(status) ? event.reason() : null,
                event.decidedBy(),
                event.decidedAt(),
                event.auditType(),
                event.tenantId());
    }

    /**
     * 读模型查询结果 → 自动决策结果
     * <p>
     * 读模型侧三列已由投影按状态分派完毕，此处为纯字段拷贝，不得再做任何反向还原。
     * </p>
     */
    public AutoDecideResult toResult(UnderwritingQueryResult result) {
        return new AutoDecideResult(
                result.getUnderwritingId(),
                result.getInsuranceId() != null ? result.getInsuranceId() : result.getPolicyId(),
                result.getStatus(),
                result.getConclusionType(),
                result.getRiskLevel(),
                result.getRiskScore(),
                result.getExtraPremiumType(),
                result.getExtraPremiumRatio(),
                result.getExtraPremiumFixedAmount(),
                result.getExtraPremiumReason(),
                result.getRejectReason(),
                result.getReviewComments(),
                result.getExclusionReason(),
                result.getUpdatedBy(),
                result.getUnderwritingCompletedTime(),
                result.getAuditType(),
                result.getTenantId());
    }

    /**
     * 取投保单号：新字段优先，存量事件缺该字段时回退 {@code policyId}
     */
    private String resolveInsuranceId(UnderwritingDecidedEvent event) {
        if (event.insuranceId() != null) {
            return event.insuranceId().value();
        }
        return event.policyId() != null ? event.policyId().value() : null;
    }

    /** 拒保/撤单类状态（口径内聚在枚举上，与投影侧 {@code isRejected()} 同源） */
    private boolean isRejected(UnderwritingEnum.UnderwritingStatus status) {
        return status != null && status.isRejected();
    }

    /** 转人工（reason 落审核意见列） */
    private boolean isManualReview(UnderwritingEnum.UnderwritingStatus status) {
        return status == UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW;
    }

    /** 除外承保（reason 落除外原因列） */
    private boolean isExcluded(UnderwritingEnum.UnderwritingStatus status) {
        return status == UnderwritingEnum.UnderwritingStatus.EXCLUDED;
    }
}
