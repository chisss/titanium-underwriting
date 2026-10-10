package com.titanium.underwriting.api.request.underwriting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.titanium.metadata.enums.customer.CustomerEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 自动决策请求（粗粒度，g02-04 / AC-05）
 * <p>
 * 面向系统调用的<b>一次成单</b>契约：入参承载投保单号 + 险种 + 保额 + 风险要素，
 * 一次调用完成「创建/幂等复用 + 提交输入 + 出具决策」并返回结论，
 * 取代此前由上游拼装的「创建 → 提交输入 → 决策」四步远程调用。
 * </p>
 * <p>
 * 🔴 {@code insuranceId} 是幂等键：同一投保单重复调用返回同一张核保单的结论，
 * 不会重复建单（上游 Saga 重试/重入安全）。
 * </p>
 */
@Schema(description = "自动决策请求（粗粒度，一次调用完成创建/复用+输入+决策）")
@Data
public class AutoDecideUnderwritingRequest {

    @Schema(description = "投保单号（幂等键，必填）")
    private String     insuranceId;

    @Schema(description = "客户ID（投保人）")
    private String     customerId;

    @Schema(description = "保额")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY,USD")
    private String     currency;

    @Schema(description = "险种编码（供核保域按产品查配置）")
    private String     productCode;

    @Schema(description = "核保类型，缺省按新单核保", example = "NEW_BUSINESS")
    private UnderwritingEnum.UnderwritingType underwritingType;

    @Schema(description = "操作人；出单主链路应以系统主体上报，缺省回落系统主体")
    private String     operatorId;

    @Schema(description = "被保人年龄（未提供时不写入，禁止用 0 占位）")
    private Integer    age;

    @Schema(description = "被保人性别", example = "MALE,FEMALE")
    private CustomerEnum.CustomerGender gender;

    @Schema(description = "被保人职业类别（1-6，未提供时不得填 0）")
    private Integer    occupationCategory;

    @Schema(description = "被保人体重指数 BMI")
    private BigDecimal bmi;

    @Schema(description = "健康告知（未提供时不写入，禁止以默认值代填）")
    private HealthDeclarationInput healthDeclaration;

    /**
     * 健康告知（嵌套对象，G12/g12-01 AC-01）
     * <p>
     * 由上游（policy 域出单 Saga 从投保险种段的段级 {@code extendData} 提取）随自动决策请求透传。
     * 字段形态与本域既有提交入参 {@code SubmitUnderwritingInputApiRequest.HealthDeclarationInput}
     * 逐项对齐（病史两项为字符串列表），使一次成单的粗粒度契约同样能表达告知要素。
     * </p>
     * <p>
     * 🔴 <b>吸烟项用包装类型 {@code Boolean}</b>：整块字段为 null 表示「未提供」；而吸烟是核保域
     * {@code HealthDeclaration} 的必答项——块存在即须给出该答案，缺失（null）由服务端翻译层显式拒绝
     * （{@code FIELD_REQUIRED}），<b>不得</b>静默按「不吸烟」处理（那会把「没告知」变成「低风险告知」）。
     * </p>
     */
    @Data
    public static class HealthDeclarationInput {

        @Schema(description = "既往病史（无则空数组）")
        private List<String> medicalHistory;

        @Schema(description = "家族遗传病史（无则空数组）")
        private List<String> familyHistory;

        @Schema(description = "是否吸烟（必答，缺失即拒绝）")
        private Boolean      smoking;

        @Schema(description = "身高（厘米，必答且为正）")
        private BigDecimal   heightCm;

        @Schema(description = "体重（千克，必答且为正）")
        private BigDecimal   weightKg;

        @Schema(description = "自定义告知项答案（键=问题编码常量名，值=true/false；可空=上游未提供）")
        private Map<String, String> answers;
    }
}
