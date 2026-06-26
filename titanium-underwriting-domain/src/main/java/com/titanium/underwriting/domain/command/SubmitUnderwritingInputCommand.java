package com.titanium.underwriting.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.underwriting.domain.valueobject.UnderwritingId;
import com.titanium.underwriting.domain.valueobject.UnderwritingInput;

/**
 * 提交险种专属核保输入命令
 * <p>
 * 在核保单创建后、核保决策前，提交健康告知/体检/职业/车辆风险等险种专属输入。
 * 输入将用于后续风险等级评估与核保结论判定。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public record SubmitUnderwritingInputCommand(
        @TargetAggregateIdentifier UnderwritingId underwritingId,
        UnderwritingInput underwritingInput,
        String submittedBy,
        String tenantId
) {
}
