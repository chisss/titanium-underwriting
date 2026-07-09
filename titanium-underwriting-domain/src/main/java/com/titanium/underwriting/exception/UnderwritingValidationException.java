package com.titanium.underwriting.exception;

import com.titanium.metadata.exception.CommandValidationException;

/**
 * 核保命令校验异常
 * <p>
 * 当核保领域的命令参数或值对象校验失败时抛出，如必填字段为空、金额为负等。
 * 继承 metadata 统一异常体系的 {@link CommandValidationException}，
 * 携带统一错误码 {@code COMMAND_VALIDATION_FAILED} 及命令名、字段名等上下文，
 * 替代领域层中裸抛的 {@code IllegalArgumentException}。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class UnderwritingValidationException extends CommandValidationException {

    /**
     * 构造核保校验异常
     *
     * @param commandName       命令名（或值对象名）
     * @param validationMessage 校验失败描述
     */
    public UnderwritingValidationException(String commandName, String validationMessage) {
        super(commandName, validationMessage);
    }

    /**
     * 构造核保校验异常（含字段名）
     *
     * @param commandName       命令名（或值对象名）
     * @param fieldName         校验失败字段名
     * @param validationMessage 校验失败描述
     */
    public UnderwritingValidationException(String commandName, String fieldName, String validationMessage) {
        super(commandName, fieldName, validationMessage);
    }
}
