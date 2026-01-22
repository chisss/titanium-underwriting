package com.titanium.underwriting.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.CurrencyEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "创建核保请求DTO")
@Data
public class CreateUnderwritingRequest {
    @Schema(description = "保单ID")
    private String        policyId;
    @Schema(description = "客户ID")
    private String        customerId;
    @Schema(description = "保额")
    private BigDecimal    amount;
    @Schema(description = "币种", exampleClasses = CurrencyEnum.class, example = "CNY,USD")
    private String        currency;
    @Schema(description = "核保类型")
    private String        underwritingType;
    @Schema(description = "请求日期")
    private LocalDateTime requestDate;
    @Schema(description = "请求人")
    private String        requestBy;
}
