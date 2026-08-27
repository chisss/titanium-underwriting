package com.titanium.underwriting.valueobject;

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
}
