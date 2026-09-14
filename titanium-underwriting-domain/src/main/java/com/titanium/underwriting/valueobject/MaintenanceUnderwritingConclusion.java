package com.titanium.underwriting.valueobject;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

/** 保全风险评估结论，独立于新单核保结论语义。 */
public enum MaintenanceUnderwritingConclusion {
    NOT_REQUIRED,
    APPROVED,
    CONDITIONAL_APPROVED,
    MANUAL_REVIEW,
    REJECTED;

    public boolean completed() {
        return this != MANUAL_REVIEW;
    }

    /**
     * 保全核保结论映射核保状态（充血模型）
     * <p>
     * 结论 → 状态的唯一口径：聚合回放（写侧）与读模型投影（读侧）共用，避免两处各写一份 switch 造成漂移。
     * 无需核保与通过均落 APPROVED；条件承保落 RATED（次标准体加费）；转人工落 MANUAL_REVIEW；拒保落 DECLINED。
     * </p>
     *
     * @return 对应的核保状态
     */
    public UnderwritingEnum.UnderwritingStatus toUnderwritingStatus() {
        return switch (this) {
            case NOT_REQUIRED, APPROVED -> UnderwritingEnum.UnderwritingStatus.APPROVED;
            case CONDITIONAL_APPROVED -> UnderwritingEnum.UnderwritingStatus.RATED;
            case MANUAL_REVIEW -> UnderwritingEnum.UnderwritingStatus.MANUAL_REVIEW;
            case REJECTED -> UnderwritingEnum.UnderwritingStatus.DECLINED;
        };
    }
}
