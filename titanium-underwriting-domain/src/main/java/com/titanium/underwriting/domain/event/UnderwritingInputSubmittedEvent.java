package com.titanium.underwriting.domain.event;

import java.time.LocalDateTime;

import com.titanium.underwriting.domain.valueobject.UnderwritingId;
import com.titanium.underwriting.domain.valueobject.UnderwritingInput;

/**
 * 险种专属核保输入已提交事件
 * <p>
 * 当核保单成功接收险种专属输入（健康告知/体检/职业/车辆风险）时发布。
 * 事件溯源时用于回放核保输入状态。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public record UnderwritingInputSubmittedEvent(
        UnderwritingId underwritingId,
        UnderwritingInput underwritingInput,
        LocalDateTime submittedAt,
        String submittedBy,
        String tenantId
) {
}
