package com.glimmer.common.exception;

import com.glimmer.common.api.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常出口。业务异常返回可预期错误码，未知异常统一 50000 并记录堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Result<Void>> handleRateLimit(RateLimitException e) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()));
        headers.set("X-RateLimit-Limit", String.valueOf(e.limit()));
        headers.set("X-RateLimit-Remaining", "0");
        return ResponseEntity.status(e.errorCode().status())
                .headers(headers)
                .body(Result.error(e.errorCode(), e.getMessage()));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.errorCode().status())
                .body(Result.error(e.errorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return badRequest(firstFieldMessage(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        return badRequest(firstFieldMessage(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(ErrorCode.INVALID_PARAM.message());
        return badRequest(message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        return badRequest(ErrorCode.INVALID_PARAM.message());
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        return badRequest(ErrorCode.INVALID_PARAM.message());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.UPLOAD_TOO_LARGE.status())
                .body(Result.error(ErrorCode.UPLOAD_TOO_LARGE, ErrorCode.UPLOAD_TOO_LARGE.message()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(Result.error(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.message()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("duplicate key: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.STATE_CONFLICT.status())
                .body(Result.error(ErrorCode.STATE_CONFLICT, "数据已存在或操作冲突"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccess(DataAccessException e) {
        log.error("data access failed", e);
        return ResponseEntity.status(ErrorCode.DEPENDENCY_UNAVAILABLE.status())
                .body(Result.error(ErrorCode.DEPENDENCY_UNAVAILABLE, "存储服务暂不可用"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(Result.error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message()));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.INVALID_PARAM.status())
                .body(Result.error(ErrorCode.INVALID_PARAM, message));
    }

    private String firstFieldMessage(FieldError fieldError) {
        if (fieldError == null || fieldError.getDefaultMessage() == null) {
            return ErrorCode.INVALID_PARAM.message();
        }
        return fieldError.getDefaultMessage();
    }
}
