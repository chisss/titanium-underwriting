package com.titanium.underwriting.web.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
 * 本类给出与全仓一致的最小契约：统一 {@code ApiResponse} 信封；领域规则违例 400；
 * 未预期异常 500 且<b>只回固定文案</b>（CWE-209：原始消息与堆栈只进日志）。
 * </p>
 * <p>
 * 本域自定义异常均继承 {@link DomainException}，故由 {@code handleDomainException} 一并覆盖。
 * 注：本域 web 层不使用 jakarta.validation（无 @Valid/@Validated），故不处理
 *     {@code ConstraintViolationException}，避免为无来源的异常引入依赖。
 * 错误码粒度细化（按错误码类别映射 HTTP 语义）属异常体系统一改造的范围。
 * </p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.titanium.underwriting.web")
public class UnderwritingExceptionHandler {

    /** 领域规则违例 → 400（真实错误码记日志；响应体暂用通用码，细化见异常体系统一改造） */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException exception) {
        log.warn("[核保] 领域规则拒绝: errorCode={}, message={}",
                exception.getErrorCode(), exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(SystemErrorCode.PARAM_INVALID, exception.getMessage()));
    }

    /** 请求参数校验失败 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        log.warn("[核保] 请求参数校验失败: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(SystemErrorCode.PARAM_INVALID, "请求参数不合法"));
    }

    /** 兜底 → 500（固定文案；原始消息与堆栈只进日志，不回显给调用方） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("[核保] 未处理异常，请求处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(SystemErrorCode.SYSTEM_ERROR, "系统内部错误，请稍后重试"));
    }
}
