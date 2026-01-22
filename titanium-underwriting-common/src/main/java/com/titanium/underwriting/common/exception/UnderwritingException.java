package com.titanium.underwriting.common.exception;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Underwriting Service Exception
 */
@Getter
public class UnderwritingException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;

    public UnderwritingException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public UnderwritingException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static UnderwritingException of(String errorCode, String errorMessage) {
        return new UnderwritingException(errorCode, errorMessage);
    }

    public static UnderwritingException of(String errorCode, String errorMessage, Throwable cause) {
        return new UnderwritingException(errorCode, errorMessage, cause);
    }

    @Override
    public String toString() {
        return StringUtils.defaultString(getErrorCode(), "") + ": " + StringUtils.defaultString(getErrorMessage(), "");
    }
}
