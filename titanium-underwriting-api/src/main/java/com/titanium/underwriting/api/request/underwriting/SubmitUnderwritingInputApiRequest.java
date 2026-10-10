package com.titanium.underwriting.api.request.underwriting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;

import lombok.Data;

/**
 * 提交核保结构化输入请求（Feign 跨域契约）
 * <p>
 * api 层自包含，不依赖 domain 值对象，供 policy 出单调用提交被保人风险信息，
 * 触发富核保评分决策（替代旧"金额>10万"兜底路径）。
 * </p>
 * <p>
 * <b>G02/AC-01 追加被保人基础要素</b>：{@link #age} / {@link #gender} 为承保前置链路
 * （出单主链路）可直接提供的粗粒度要素，此前契约无对应字段，导致保单域侧已装配好的
 * 年龄/性别被<b>静默丢弃</b>。四要素（年龄/性别/职业类别/BMI）均为可选，未提供时须传
 * {@code null} 而<b>不得</b>用 0/空串占位——核保域按「未提供」与「提供了 0 岁」区分处理。
 * </p>
 */
@Data
public class SubmitUnderwritingInputApiRequest {

    /** 提交人 */
    private String                submittedBy;

    /** 被保人年龄（可为 null；提供时须在 0-150 之间） */
    private Integer               age;

    /** 被保人性别（可为 null） */
    private CustomerGender        gender;

    /** 健康告知输入 */
    private HealthDeclarationInput healthDeclaration;

    /** 体检结果输入 */
    private PhysicalExamInput     physicalExamResult;

    /** 职业信息输入 */
    private OccupationInput       occupationInfo;

    /** 财务评估输入 */
    private FinancialAssessInput  financialAssessment;

    /** 健康告知（病史/家族史/吸烟/BMI 等） */
    @Data
    public static class HealthDeclarationInput {
        private List<String> medicalHistory;
        private List<String> familyHistory;
        private boolean      smoking;
        private BigDecimal   heightCm;
        private BigDecimal   weightKg;
        /** 自定义告知项答案（键=问题编码常量名，值=true/false；G12/g12-02） */
        private Map<String, String> answers;
    }

    /** 体检结果（血压/血糖/BMI 等） */
    @Data
    public static class PhysicalExamInput {
        private BigDecimal   bmi;
        private BigDecimal   systolicPressure;
        private BigDecimal   diastolicPressure;
        private BigDecimal   bloodGlucose;
        private List<String> abnormalItems;
    }

    /** 职业信息 */
    @Data
    public static class OccupationInput {
        private String     occupationName;
        private int        occupationCategory;
        private BigDecimal riskFactor;
    }

    /** 财务评估（高保额寿险财务核保） */
    @Data
    public static class FinancialAssessInput {
        private BigDecimal annualIncome;
        private BigDecimal netWorth;
        private BigDecimal requestedSumInsured;
        private String     incomeSource;
    }
}
