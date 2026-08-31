package com.titanium.underwriting.exception;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.metadata.exception.IllegalStateTransitionException;

/**
 * 核保状态流转异常
 * <p>
 * 当核保聚合根在非法状态下执行操作或发生非法状态流转时抛出，
 * 如对非待核保状态的核保单再次发起核保。继承 metadata 统一异常体系的
 * {@link IllegalStateTransitionException}，必须携带 {@link UnderwritingErrorCode}
 * 枚举码（72 段）及核保ID、源状态、目标状态等上下文，替代裸串错误码。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class UnderwritingStatusException extends IllegalStateTransitionException {

    /** 聚合根类型名（用于异常消息上下文） */
    private static final String AGGREGATE_TYPE = "Underwriting";

    /** 旧裸串构造器的兜底错误码（新代码禁止使用裸串构造） */
    private static final UnderwritingErrorCode DEFAULT_ERROR_CODE = UnderwritingErrorCode.ILLEGAL_STATUS_TRANSITION;

    /**
     * 构造核保状态流转异常（标准错误码枚举）
     *
     * @param errorCode      核保域错误码枚举
     * @param underwritingId 核保ID
     * @param fromStatus     源状态
     * @param toStatus       目标状态（或尝试执行的操作）
     */
    public UnderwritingStatusException(UnderwritingErrorCode errorCode, String underwritingId,
                                       String fromStatus, String toStatus) {
        super(errorCode, AGGREGATE_TYPE, underwritingId, fromStatus, toStatus);
    }

    /**
     * 构造核保状态流转异常（标准错误码枚举 + 含原因说明）
     *
     * @param errorCode      核保域错误码枚举
     * @param underwritingId 核保ID
     * @param fromStatus     源状态
     * @param toStatus       目标状态（或尝试执行的操作）
     * @param reason         非法原因说明
     */
    public UnderwritingStatusException(UnderwritingErrorCode errorCode, String underwritingId,
                                       String fromStatus, String toStatus, String reason) {
        super(errorCode, AGGREGATE_TYPE, underwritingId, fromStatus, toStatus, reason);
    }

    /**
     * @deprecated 裸串构造器无法国际化，请改用
     * {@link #UnderwritingStatusException(UnderwritingErrorCode, String, String, String)}
     */
    @Deprecated
    public UnderwritingStatusException(String underwritingId, String fromStatus, String toStatus) {
        super(DEFAULT_ERROR_CODE, AGGREGATE_TYPE, underwritingId, fromStatus, toStatus);
    }

    /**
     * @deprecated 裸串构造器无法国际化，请改用
     * {@link #UnderwritingStatusException(UnderwritingErrorCode, String, String, String, String)}
     */
    @Deprecated
    public UnderwritingStatusException(String underwritingId, String fromStatus, String toStatus, String reason) {
        super(DEFAULT_ERROR_CODE, AGGREGATE_TYPE, underwritingId, fromStatus, toStatus, reason);
    }
}
