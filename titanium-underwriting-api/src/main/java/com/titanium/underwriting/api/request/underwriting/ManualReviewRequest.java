package com.titanium.underwriting.api.request.underwriting;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 转人工审核请求（Feign 契约入参，上游 → 本域）
 * <p>
 * 把核保件置为人工复核状态（{@code MANUAL_REVIEW}）。服务端实现为
 * {@code web/provider/UnderwritingApiProvider}，经映射器汇入与 web 端点同一个
 * {@code UnderwritingCommandService#manualReview}。
 * </p>
 */
@Schema(description = "转人工审核请求")
@Data
public class ManualReviewRequest {

    @Schema(description = "转人工原因/审核意见", example = "保额超阈值，需人工复核")
    private String reviewComments;

    @Schema(description = "审核人")
    private String reviewedBy;
}
