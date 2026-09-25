package com.titanium.underwriting.web.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.titanium.common.boundary.ExceptionResponseFactory;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 核保 Web 全局异常处理器
 * <p>
 * 本域此前没有 {@code @RestControllerAdvice}：未预期异常的响应体是 Spring Boot 默认的
 * {@code {timestamp,status,error,path}} —— <b>没有业务错误码</b>，且与其它域的
 * {@code ApiResponse} 信封不一致，调用方（含 admin BFF）只能按域猜信封（审查报告 EXC-02）。
 * </p>
 * <p>
 * 本类给出与全仓一致的最小契约：统一 {@code ApiResponse} 信封；业务异常的状态与错误码取自
 * <b>异常自身</b>（经 {@link ExceptionResponseFactory} 按 {@code BaseErrorCode.category()} 映射），
 * 不再一律 400 + {@code PARAM_INVALID}；未预期异常 500 且<b>只回固定文案</b>
 * （CWE-209：原始消息与堆栈只进日志）。
 * </p>
 * <p>
 * 本域自定义异常均继承 {@link DomainException}，故由 {@code handleDomainException} 一并覆盖。
 * Spring MVC 的框架异常（畸形报文、404、405、{@code MethodArgumentNotValidException} 等）
 * 由 {@code titanium-common} 的<b>边界处理器</b>按优先级接管。
 * </p>
 * <p>
 * 本域<b>不再声明校验异常处理器</b>：本域 web 层不使用 jakarta.validation（无
 * {@code @Valid}/{@code @Validated}），`ConstraintViolationException` 连 classpath 都不在
 * （实测 `mvn dependency:build-classpath` 无 `jakarta.validation-api`），即无异常来源；
 * 而 {@code MethodArgumentNotValidException} 已被边界处理器接管。原处理器属死码，已删除。
 * </p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.titanium.underwriting.web")
public class UnderwritingExceptionHandler {

    /**
     * 业务异常 → 统一信封：状态取错误码的语义类别，响应体回显<b>异常自身的错误码</b>。
     * <p>
     * 改造前此处恒为 {@code 400 + PARAM_INVALID}，两处失真：①「资源不存在」「状态冲突」
     * 「服务端故障」全被报成 400，调用方的重试/告警策略失去依据；②异常自带的码被通用码覆盖，
     * 调用方拿到的码与真实原因无关（审查报告 EXC-02）。
     * </p>
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException exception) {
        log.warn("[核保] 领域异常: errorCode={}, message={}",
                exception.getErrorCodeValue(), exception.getMessage());
        return ExceptionResponseFactory.of(exception);
    }

    /** 兜底 → 500（固定文案；原始消息与堆栈只进日志，不回显给调用方） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("[核保] 未处理异常，请求处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(SystemErrorCode.SYSTEM_ERROR, "系统内部错误，请稍后重试"));
    }
}
