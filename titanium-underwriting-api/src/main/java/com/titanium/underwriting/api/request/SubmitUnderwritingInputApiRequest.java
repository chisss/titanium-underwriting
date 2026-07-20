package com.titanium.underwriting.api.request;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 提交核保结构化输入请求（Feign 跨域契约）
 * <p>
 * api 层自包含，不依赖 domain 值对象，供 policy 出单调用提交被保人风险信息，
 * 触发富核保评分决策（替代旧"金额>10万"兜底路径）。
 * </p>
 */
@Data
public class SubmitUnderwritingInputApiRequest {

    /** 提交人 */
    private String                submittedBy;

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
