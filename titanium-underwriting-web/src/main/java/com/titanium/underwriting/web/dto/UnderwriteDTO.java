package com.titanium.underwriting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 执行核保请求（后台/端上 HTTP 入参）
 * <p>
 * 由 {@code UnderwritingController} 接收，经 {@code UnderwritingWebAssembler} 翻译为领域命令
 * {@code UnderwriteCommand}。与对外远程契约 {@code api.request.UnderwriteDTO} 物理隔离。
 * </p>
 */
@Schema(description = "执行核保请求")
@Data
public class UnderwriteDTO {

    @Schema(description = "核保ID")
    @Size(max = 64)
    private String underwritingId;

    @Schema(description = "核保状态")
    private UnderwritingEnum.UnderwritingStatus status;

    @Schema(description = "核保原因")
    @NotBlank
    @Size(max = 1024)
    private String reason;

    @Schema(description = "核保评论")
    @Size(max = 1024)
    private String comments;

    @Schema(description = "核保时间")
    private LocalDateTime underwriteDate;

    @Schema(description = "核保人")
    @Size(max = 64)
    private String underwriteBy;

    @Schema(description = "核保金额")
    @NotNull
    private BigDecimal amount;
}
