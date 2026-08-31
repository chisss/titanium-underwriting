package com.titanium.underwriting.common.constant;

/**
 * Underwriting Service Constants
 */
public class UnderwritingConstants {
    public static final String UNDERWRITING_SERVICE_NAME  = "titanium-underwriting";
    public static final String UNDERWRITING_TOPIC         = "underwriting-events";
    /** 核保决策完成事件 topic（跨域异步回流 policy 域） */
    public static final String TOPIC_UNDERWRITING_DECIDED = "underwriting-decided";
    /** 核保创建事件 topic（写侧事件外发） */
    public static final String TOPIC_UNDERWRITING_CREATED = "underwriting-created";
    /** 核保状态变更事件 topic（写侧事件外发） */
    public static final String TOPIC_UNDERWRITING_STATUS_CHANGED = "underwriting-status-changed";
    /** 核保域跨域外发处理组 */
    public static final String KAFKA_PROCESSING_GROUP     = "underwriting-kafka-group";

    // ========== 落库/跨域业务描述常量（红线 20：禁写死中文字符串，文案渲染推迟到边界层） ==========

    /** 保全核保评估摘要：配置与风险差异共同确认无需核保 */
    public static final String MAINTENANCE_SUMMARY_NOT_REQUIRED = "配置与风险差异共同确认无需核保";
    /** 保全核保评估摘要：配置跳过核保但存在风险字段变化 */
    public static final String MAINTENANCE_SUMMARY_SKIPPED_WITH_CHANGES = "配置跳过核保但存在风险字段变化";
    /** 保全核保评估摘要：版本化规则判定风险不可接受 */
    public static final String MAINTENANCE_SUMMARY_REJECTED = "版本化规则判定风险不可接受";
    /** 保全核保评估摘要：风险信息需要核保人员复核 */
    public static final String MAINTENANCE_SUMMARY_MANUAL_REVIEW = "风险信息需要核保人员复核";
    /** 保全核保评估摘要：风险可在附加条件下接受 */
    public static final String MAINTENANCE_SUMMARY_CONDITIONAL_APPROVED = "风险可在附加条件下接受";
    /** 保全核保评估摘要：版本化规则判定风险可接受 */
    public static final String MAINTENANCE_SUMMARY_APPROVED = "版本化规则判定风险可接受";
    /** 保全核保附加条件前缀（条件承保时以「前缀+字段编码」落库） */
    public static final String MAINTENANCE_CONDITION_PREFIX = "REVIEW_FIELD:";
    /** 次标准体核保加费原因模板（参数：风险评分），随决策事件透传 billing 域 */
    public static final String EXTRA_PREMIUM_REASON_TEMPLATE = "次标准体核保加费（风险评分 %d）";
}
