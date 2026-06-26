package com.titanium.underwriting.domain.exception;

import com.titanium.metadata.exception.IllegalStateTransitionException;

/**
 * 核保状态流转异常
 * <p>
 * 当核保聚合根在非法状态下执行操作或发生非法状态流转时抛出，
 * 如对非待核保状态的核保单再次发起核保。继承 metadata 统一异常体系的
 * {@link IllegalStateTransitionException}，携带统一错误码 {@code ILLEGAL_STATE_TRANSITION}
 * 及核保ID、源状态、目标状态等上下文。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class UnderwritingStatusException extends IllegalStateTransitionException {

    /** 聚合根类型名（用于异常消息上下文） */
    private static final String AGGREGATE_TYPE = "Underwriting";

    /**
     * 构造核保状态流转异常
     *
     * @param underwritingId 核保ID
     * @param fromStatus     源状态
     * @param toStatus       目标状态（或尝试执行的操作）
     */
    public UnderwritingStatusException(String underwritingId, String fromStatus, String toStatus) {
        super(AGGREGATE_TYPE, underwritingId, fromStatus, toStatus);
    }

    /**
     * 构造核保状态流转异常（含原因说明）
     *
     * @param underwritingId 核保ID
     * @param fromStatus     源状态
     * @param toStatus       目标状态（或尝试执行的操作）
     * @param reason         非法原因说明
     */
    public UnderwritingStatusException(String underwritingId, String fromStatus, String toStatus, String reason) {
        super(AGGREGATE_TYPE, underwritingId, fromStatus, toStatus, reason);
    }
}
