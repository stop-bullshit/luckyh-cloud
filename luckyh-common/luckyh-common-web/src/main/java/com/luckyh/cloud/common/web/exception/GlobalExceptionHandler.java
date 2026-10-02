package com.luckyh.cloud.common.web.exception;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.common.core.exception.BusinessException;
import com.luckyh.cloud.common.core.exception.ServiceException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将显式业务错误和请求错误转换为统一响应。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        // 逻辑变动: 区分业务异常与服务异常-20261002-2117-1
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessException businessException) {
                return ResponseEntity.ok(Result.error(businessException.getCode(), businessException.getMessage()));
            }
            if (cause instanceof ServiceException serviceException) {
                log.warn("下游服务异常", exception);
                return ResponseEntity.status(serviceException.getCode())
                        .body(Result.error(serviceException.getCode(), serviceException.getMessage()));
            }
        }

        if (exception instanceof ConstraintViolationException || exception instanceof TypeMismatchException) {
            return ResponseEntity.badRequest().body(Result.error(400, "请求参数错误"));
        }

        if (exception instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            if (status >= 500) {
                log.error("服务处理失败", exception);
                return ResponseEntity.status(status).body(Result.error(status, "服务处理失败，请稍后重试"));
            }
            String message = status == HttpStatus.BAD_REQUEST.value() ? "请求参数错误" : "请求失败，请检查请求后重试";
            return ResponseEntity.status(status).body(Result.error(status, message));
        }

        log.error("服务处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务处理失败，请稍后重试"));
    }
}
