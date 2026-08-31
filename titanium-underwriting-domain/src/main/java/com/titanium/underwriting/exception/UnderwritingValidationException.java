package com.titanium.underwriting.exception;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 核保命令校验异常
 * <p>
 * 当核保领域的命令参数或值对象校验失败时抛出，如必填字段为空、金额为负等。
 * 继承 metadata 统一异常体系的 {@link DomainException}，必须携带
 * {@link UnderwritingErrorCode} 枚举码（72 段），替代裸串错误码。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class UnderwritingValidationException extends DomainException {

    /** 旧裸串构造器的兜底错误码（新代码禁止使用裸串构造） */
    private static final UnderwritingErrorCode DEFAULT_ERROR_CODE = UnderwritingErrorCode.UNDERWRITING_VALIDATION_FAILED;

    /**
     * 构造核保校验异常（标准错误码枚举，消息取枚举默认文案）
     *
     * @param errorCode   核保域错误码枚举
     * @param commandName 命令名（或值对象名）
     */
    public UnderwritingValidationException(UnderwritingErrorCode errorCode, String commandName) {
        super(errorCode,
                String.format("命令 %s 校验失败: %s", commandName, errorCode.getMessage()));
    }

    /**
     * 构造核保校验异常（标准错误码枚举 + 含字段名，消息取枚举默认文案）
     *
     * @param errorCode   核保域错误码枚举
     * @param commandName 命令名（或值对象名）
     * @param fieldName   校验失败字段名
     */
    public UnderwritingValidationException(UnderwritingErrorCode errorCode, String commandName, String fieldName) {
        super(errorCode,
                String.format("命令 %s 字段 %s 校验失败: %s", commandName, fieldName, errorCode.getMessage()));
    }

    /**
     * @deprecated 裸串构造器无法国际化，请改用 {@link #UnderwritingValidationException(UnderwritingErrorCode, String)}
     */
    @Deprecated
    public UnderwritingValidationException(String commandName, String validationMessage) {
        super(DEFAULT_ERROR_CODE,
                String.format("命令 %s 校验失败: %s", commandName, validationMessage));
    }

    /**
     * @deprecated 裸串构造器无法国际化，请改用
     * {@link #UnderwritingValidationException(UnderwritingErrorCode, String, String)}
     */
    @Deprecated
    public UnderwritingValidationException(String commandName, String fieldName, String validationMessage) {
        super(DEFAULT_ERROR_CODE,
                String.format("命令 %s 字段 %s 校验失败: %s", commandName, fieldName, validationMessage));
    }
}
