package com.titanium.underwriting.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "执行核保请求DTO")
@Data
public class UnderwriteRequest {
    @Schema(description = "核保ID")
    private String                              underwritingId;
    @Schema(description = "核保状态", exampleClasses = { UnderwritingEnum.UnderwritingStatus.class })
    private UnderwritingEnum.UnderwritingStatus status;
    @Schema(description = "核保原因")
    private String                              reason;
    @Schema(description = "核保评论")
    private String                              comments;
    @Schema(description = "核保时间")
    private LocalDateTime                       underwriteDate;
    @Schema(description = "核保人")
    private String                              underwriteBy;
    @Schema(description = "核保金额")
    private BigDecimal                          amount;
}
