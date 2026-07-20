package com.titanium.underwriting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建核保请求（后台/端上 HTTP 入参）
 * <p>
 * 面向人机终端的表现层请求体，由 {@code UnderwritingController} 接收，经
 * {@code UnderwritingWebMapper} 翻译为领域命令 {@code CreateUnderwritingCommand}。
 * 与对外远程契约 {@code api.request.CreateUnderwritingDTO} 物理隔离，互不耦合。
 * </p>
 */
@Schema(description = "创建核保请求")
@Data
public class CreateUnderwritingDTO {

    @Schema(description = "保单ID")
    private String policyId;

    @Schema(description = "客户ID")
    private String customerId;

    @Schema(description = "保额")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "核保类型", example = "NEW_BUSINESS")
    private UnderwritingEnum.UnderwritingType underwritingType;

    @Schema(description = "请求日期")
    private LocalDateTime requestDate;

    @Schema(description = "请求人")
    private String requestBy;

    @Schema(description = "险种编码(UW-4:供核保域按产品查配置)")
    private String productCode;
}
