package com.titanium.underwriting.common.exception;

import org.apache.commons.lang3.StringUtils;

import com.titanium.metadata.errorcode.UnderwritingErrorCode;

import lombok.Getter;

/**
 * Underwriting Service Exception
 * <p>
 * 核保域通用服务异常：携带 {@link UnderwritingErrorCode} 标准错误码枚举（72 段），
 * 裸串错误码构造器已废弃，新代码禁止使用。
 * </p>
 */
@Getter
public class UnderwritingException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;

    /**
     * 构造核保服务异常（标准错误码枚举，消息取枚举默认文案）
     *
     * @param errorCode 核保域错误码枚举
     */
    public UnderwritingException(UnderwritingErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorCode.getMessage();
    }

    /**
     * 构造核保服务异常（标准错误码枚举 + 自定义消息）
     *
     * @param errorCode    核保域错误码枚举
     * @param errorMessage 错误消息（覆盖枚举默认文案）
     */
    public UnderwritingException(UnderwritingErrorCode errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorMessage;
    }

    /**
     * 构造核保服务异常（标准错误码枚举 + 根因异常）
     *
     * @param errorCode    核保域错误码枚举
     * @param errorMessage 错误消息
     * @param cause        根因异常
     */
    public UnderwritingException(UnderwritingErrorCode errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorMessage;
    }

    /**
     * @deprecated 裸串错误码无法国际化，请改用 {@link #UnderwritingException(UnderwritingErrorCode, String)}
     */
    @Deprecated
    public UnderwritingException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * @deprecated 裸串错误码无法国际化，请改用
     * {@link #UnderwritingException(UnderwritingErrorCode, String, Throwable)}
     */
    @Deprecated
    public UnderwritingException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static UnderwritingException of(UnderwritingErrorCode errorCode) {
        return new UnderwritingException(errorCode);
    }

    public static UnderwritingException of(UnderwritingErrorCode errorCode, String errorMessage) {
        return new UnderwritingException(errorCode, errorMessage);
    }

    public static UnderwritingException of(UnderwritingErrorCode errorCode, String errorMessage, Throwable cause) {
        return new UnderwritingException(errorCode, errorMessage, cause);
    }

    /**
     * @deprecated 裸串错误码无法国际化，请改用 {@link #of(UnderwritingErrorCode, String)}
     */
    @Deprecated
    public static UnderwritingException of(String errorCode, String errorMessage) {
        return new UnderwritingException(errorCode, errorMessage);
    }

    /**
     * @deprecated 裸串错误码无法国际化，请改用
     * {@link #of(UnderwritingErrorCode, String, Throwable)}
     */
    @Deprecated
    public static UnderwritingException of(String errorCode, String errorMessage, Throwable cause) {
        return new UnderwritingException(errorCode, errorMessage, cause);
    }

    @Override
    public String toString() {
        return StringUtils.defaultString(getErrorCode(), "") + ": " + StringUtils.defaultString(getErrorMessage(), "");
    }
}
