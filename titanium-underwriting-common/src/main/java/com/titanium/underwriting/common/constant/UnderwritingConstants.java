package com.titanium.underwriting.common.constant;

import java.util.List;


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

    // ========== 规则引擎核保链路常量（dev-505：产品核保配置→规则集→核保执行） ==========

    /** 规则引擎决策结论原文：通过（回退聚合内置评分路径） */
    public static final String RULE_CONCLUSION_PASS     = "PASS";
    /** 规则引擎决策结论原文：拒绝 */
    public static final String RULE_CONCLUSION_REJECT   = "REJECT";
    /** 规则引擎决策结论原文：转人工复核 */
    public static final String RULE_CONCLUSION_REFER    = "REFER";
    /** 规则引擎决策结论原文：加费承保（G16 新增结论） */
    public static final String RULE_CONCLUSION_SURCHARGE = "SURCHARGE";
    /** 规则动作参数键：加费率（如 0.2 表示加费 20%） */
    public static final String RULE_ACTION_KEY_SURCHARGE_RATE = "surchargeRate";
    /** 规则动作参数键：原因说明 */
    public static final String RULE_ACTION_KEY_REASON = "reason";
    /** 规则引擎加费原因文案（参数：规则集结论原文），加费明细随事件透传 billing 域 */
    public static final String RULE_EXTRA_PREMIUM_REASON_TEMPLATE = "规则引擎核保加费（规则集结论 %s）";

    /** 规则上下文变量名：核保金额 */
    public static final String RULE_VAR_SUM_INSURED = "sumInsured";
    /** 规则上下文变量名：既往病史清单 */
    public static final String RULE_VAR_MEDICAL_HISTORY = "medicalHistory";
    /** 规则上下文变量名：是否吸烟 */
    public static final String RULE_VAR_SMOKING = "smoking";
    /** 规则上下文变量名：BMI */
    public static final String RULE_VAR_BMI = "bmi";
    /** 规则上下文变量名：职业类别（1-6 类，类别越高风险越大） */
    public static final String RULE_VAR_OCCUPATION_CATEGORY = "occupationCategory";
    /** 规则上下文变量名：职业危险系数 */
    public static final String RULE_VAR_OCCUPATION_RISK_FACTOR = "occupationRiskFactor";
    /** 规则上下文变量名：车龄（年） */
    public static final String RULE_VAR_VEHICLE_AGE_YEARS = "vehicleAgeYears";
    /** 规则上下文变量名：车辆使用性质 */
    public static final String RULE_VAR_VEHICLE_USAGE_NATURE = "usageNature";
    /** 规则上下文变量名：历史出险次数 */
    public static final String RULE_VAR_HISTORICAL_CLAIM_COUNT = "historicalClaimCount";
    /** 规则上下文变量名：NCD 系数 */
    public static final String RULE_VAR_NCD_FACTOR = "ncdFactor";
    /** 规则上下文变量名：年收入 */
    public static final String RULE_VAR_ANNUAL_INCOME = "annualIncome";
    /** 规则上下文变量名：净资产 */
    public static final String RULE_VAR_NET_WORTH = "netWorth";
    /** 规则上下文变量名：申请保额（财务评估） */
    public static final String RULE_VAR_REQUESTED_SUM_INSURED = "requestedSumInsured";

    /**
     * 特征中心特征契约清单（dev-505/G21）：规则条件引用的派生特征编码，逐特征提取、失败跳过。
     * 特征字典属丰富化输入，规则引擎 G13 变量预检是缺失变量的最终防线；
     * 规则集新增派生特征变量时，在此追加对应特征编码。
     */
    public static final List<String> UNDERWRITING_FEATURE_CODES =
            List.of("occupationRiskLevel", "bmiLevel", "claimHistoryCount");
}
