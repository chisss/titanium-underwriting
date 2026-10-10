package com.titanium.underwriting.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转人工审核请求（后台/端上 HTTP 入参）
 * <p>
 * 把核保件置为人工复核状态（{@code MANUAL_REVIEW}）。由 {@code UnderwritingController} 接收，
 * 经 {@code UnderwritingWebAssembler} 翻译为领域命令 {@code ManualReviewCommand}；
 * 与面向其它微服务的 {@code ManualReviewRequest} 平行收敛到同一应用层门面。
 * </p>
 */
@Schema(description = "转人工审核请求")
@Data
public class ManualReviewDTO {

    @Schema(description = "转人工原因/审核意见", example = "保额超阈值，需人工复核")
    @Size(max = 500)
    private String reviewComments;

    @Schema(description = "审核人")
    @Size(max = 64)
    private String reviewedBy;
}
