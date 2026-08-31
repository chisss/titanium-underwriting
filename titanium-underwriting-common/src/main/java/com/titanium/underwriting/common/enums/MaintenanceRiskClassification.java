package com.titanium.underwriting.common.enums;

import java.util.Locale;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保全风险字段变更分类码（规范化枚举，实现 {@link BaseEnum}）
 * <p>
 * 保全域上报的风险字段变更 {@code changeTypeCode} 按前缀分类为核保处置结论，
 * 取代聚合根内裸字符串 {@code startsWith("UW_REJECT")} 硬编码分支（红线 20）。
 * 分类规则保持与历史行为一致：{@code UW_REJECT*}/{@code UW_MANUAL*}/{@code UW_CONDITIONAL*}
 * 按前缀匹配，{@code COVERAGE_AMOUNT_CHANGE} 精确匹配，{@code UW_AUTO_ACCEPT*} 自动接受，
 * 其余未识别编码一律转人工复核（保守策略）。
 * </p>
 * <p>
 * 实现 {@link BaseEnum} 统一四标准属性：{@code code} 为语言无关稳定标识（即分类常量名，
 * 独立于可变的前缀规则字段 {@code codePrefix}），{@code name} 为默认展示名；{@code enumCode}
 * 不定义（分类仅内存使用，不落库/跨系统传输）。前缀匹配规则字段 {@code codePrefix} 语义不变。
 * </p>
 */
@Getter
public enum MaintenanceRiskClassification implements BaseEnum {

    /** 拒保类变更（前缀 {@code UW_REJECT}） */
    REJECT("UW_REJECT", MaintenanceRiskVerdict.REJECT, "REJECT", "拒保"),
    /** 人工复核类变更（前缀 {@code UW_MANUAL}） */
    MANUAL_REVIEW("UW_MANUAL", MaintenanceRiskVerdict.MANUAL_REVIEW, "MANUAL_REVIEW", "转人工复核"),
    /** 条件承保类变更（前缀 {@code UW_CONDITIONAL}） */
    CONDITIONAL("UW_CONDITIONAL", MaintenanceRiskVerdict.CONDITIONAL, "CONDITIONAL", "条件承保"),
    /** 保额变更（精确匹配 {@code COVERAGE_AMOUNT_CHANGE}），按条件承保处置 */
    COVERAGE_AMOUNT_CHANGE("COVERAGE_AMOUNT_CHANGE", MaintenanceRiskVerdict.CONDITIONAL, "COVERAGE_AMOUNT_CHANGE",
            "保额变更"),
    /** 自动接受类变更（前缀 {@code UW_AUTO_ACCEPT}） */
    AUTO_ACCEPT("UW_AUTO_ACCEPT", MaintenanceRiskVerdict.ACCEPT, "AUTO_ACCEPT", "自动接受"),
    /** 未识别编码：保守转人工复核 */
    UNCLASSIFIED(null, MaintenanceRiskVerdict.MANUAL_REVIEW, "UNCLASSIFIED", "未识别编码");

    /** 变更类型编码前缀（精确/前缀匹配规则见 {@link #of}） */
    private final String codePrefix;

    /** 分类对应的核保处置方向 */
    private final MaintenanceRiskVerdict verdict;

    /** 语言无关稳定标识（{@link BaseEnum} 标准属性） */
    private final String code;

    /** 默认展示名（{@link BaseEnum} 标准属性） */
    private final String name;

    MaintenanceRiskClassification(String codePrefix, MaintenanceRiskVerdict verdict, String code, String name) {
        this.codePrefix = codePrefix;
        this.verdict = verdict;
        this.code = code;
        this.name = name;
    }

    /**
     * 根据 code 反查枚举，未匹配返回 null（统一范式入口，委托 {@link BaseEnum}）
     */
    public static MaintenanceRiskClassification fromCode(String code) {
        return BaseEnum.fromCode(MaintenanceRiskClassification.class, code);
    }

    /**
     * 按变更类型编码分类（规则与历史裸串分支完全一致）
     * <p>
     * 匹配顺序：{@code UW_REJECT} → {@code UW_MANUAL} → {@code UW_CONDITIONAL} → 精确
     * {@code COVERAGE_AMOUNT_CHANGE} → {@code UW_AUTO_ACCEPT} → 兜底未识别。
     * </p>
     *
     * @param changeTypeCode 变更类型编码（大小写不敏感，可为空）
     * @return 风险变更分类
     */
    public static MaintenanceRiskClassification of(String changeTypeCode) {
        if (changeTypeCode == null || changeTypeCode.isBlank()) {
            return UNCLASSIFIED;
        }
        String normalized = changeTypeCode.toUpperCase(Locale.ROOT);
        if (normalized.startsWith(REJECT.codePrefix)) {
            return REJECT;
        }
        if (normalized.startsWith(MANUAL_REVIEW.codePrefix)) {
            return MANUAL_REVIEW;
        }
        if (normalized.startsWith(CONDITIONAL.codePrefix)) {
            return CONDITIONAL;
        }
        if (COVERAGE_AMOUNT_CHANGE.codePrefix.equals(normalized)) {
            return COVERAGE_AMOUNT_CHANGE;
        }
        if (normalized.startsWith(AUTO_ACCEPT.codePrefix)) {
            return AUTO_ACCEPT;
        }
        return UNCLASSIFIED;
    }

    /** 保全风险分类的核保处置方向（实现 {@link BaseEnum} 统一标准属性） */
    @Getter
    public enum MaintenanceRiskVerdict implements BaseEnum {
        /** 拒保 */
        REJECT("REJECT", "拒保"),
        /** 转人工复核 */
        MANUAL_REVIEW("MANUAL_REVIEW", "转人工复核"),
        /** 条件承保（附加条件） */
        CONDITIONAL("CONDITIONAL", "条件承保"),
        /** 自动接受 */
        ACCEPT("ACCEPT", "自动接受");

        /** 语言无关稳定标识（{@link BaseEnum} 标准属性） */
        private final String code;

        /** 默认展示名（{@link BaseEnum} 标准属性） */
        private final String name;

        MaintenanceRiskVerdict(String code, String name) {
            this.code = code;
            this.name = name;
        }

        /**
         * 根据 code 反查枚举，未匹配返回 null（统一范式入口，委托 {@link BaseEnum}）
         */
        public static MaintenanceRiskVerdict fromCode(String code) {
            return BaseEnum.fromCode(MaintenanceRiskVerdict.class, code);
        }
    }
}
