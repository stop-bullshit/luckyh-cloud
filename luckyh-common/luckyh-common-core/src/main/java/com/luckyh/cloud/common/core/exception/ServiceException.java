package com.luckyh.cloud.common.core.exception;

/**
 * 下游服务不可用或响应超时的异常。
 *
 * @author Lucky
 * @since 2026-10-02
 */
public class ServiceException extends RuntimeException {

    private final int code;

    public ServiceException(int code, String message) {
        super(message);
        if (code != 503 && code != 504) {
            throw new IllegalArgumentException("服务异常状态码仅支持 503 或 504");
        }
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
