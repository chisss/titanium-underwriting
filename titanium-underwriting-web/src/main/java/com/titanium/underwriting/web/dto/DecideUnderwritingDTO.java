package com.titanium.underwriting.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 核保决策请求（后台/端上 HTTP 入参）
 * <p>
 * 触发基于已提交结构化输入的智能核保决策，产出核保结论（ACCEPT/MODIFY/REJECT/POSTPONE）与风险等级。
 * 由 {@code UnderwritingController} 接收，经 {@code UnderwritingWebAssembler} 翻译为领域命令
 * {@code DecideUnderwritingCommand}。
 * </p>
 */
@Schema(description = "核保决策请求")
@Data
public class DecideUnderwritingDTO {

    @Schema(description = "核保方式：AUTOMATIC(自动)/MANUAL(人工)/HYBRID(混合)", example = "AUTOMATIC")
    private String auditType;

    @Schema(description = "决策人")
    private String decidedBy;
}
