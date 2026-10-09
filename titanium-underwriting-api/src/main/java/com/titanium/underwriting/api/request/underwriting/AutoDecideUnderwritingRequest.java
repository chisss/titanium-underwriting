package com.titanium.underwriting.api.request.underwriting;

import java.math.BigDecimal;

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
}
