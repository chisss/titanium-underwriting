package com.titanium.underwriting.web.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 提交核保输入请求（后台/端上 HTTP 入参）
 * <p>
 * 承载四类险种专属核保输入（健康告知/体检/职业/车辆风险），按险种填充对应字段，
 * 未填充的整块为 {@code null}（寿险填健康+体检、意外险填职业、车险填车辆）。
 * 由 {@code UnderwritingController} 接收，经 {@code UnderwritingWebMapper} 装配为领域命令
 * {@code SubmitUnderwritingInputCommand}。
 * </p>
 */
@Schema(description = "提交核保输入请求")
@Data
public class SubmitUnderwritingInputDTO {

    @Schema(description = "提交人")
    private String submittedBy;

    @Schema(description = "健康告知（寿险/重疾/医疗），不适用则为空")
    private HealthDeclarationInput healthDeclaration;

    @Schema(description = "体检报告（高保额寿险/重疾），不适用则为空")
    private PhysicalExamInput physicalExamResult;

    @Schema(description = "职业信息（意外险/定期寿险），不适用则为空")
    private OccupationInput occupationInfo;

    @Schema(description = "车辆风险信息（车险），不适用则为空")
    private VehicleRiskInput vehicleRiskInfo;

    /** 健康告知输入 */
    @Data
    public static class HealthDeclarationInput {
        private List<String> medicalHistory;
        private List<String> familyHistory;
        private boolean      smoking;
        private BigDecimal   heightCm;
        private BigDecimal   weightKg;
    }

    /** 体检报告输入 */
    @Data
    public static class PhysicalExamInput {
        private BigDecimal   bmi;
        private BigDecimal   systolicPressure;
        private BigDecimal   diastolicPressure;
        private BigDecimal   bloodGlucose;
        private List<String> abnormalItems;
    }

    /** 职业信息输入 */
    @Data
    public static class OccupationInput {
        private String     occupationName;
        private int        occupationCategory;
        private BigDecimal riskFactor;
    }

    /** 车辆风险输入 */
    @Data
    public static class VehicleRiskInput {
        private int        vehicleAgeYears;
        /** 使用性质：FAMILY/NON_OPERATING/OPERATING */
        private String     usageNature;
        private int        historicalClaimCount;
        private BigDecimal ncdFactor;
    }
}
