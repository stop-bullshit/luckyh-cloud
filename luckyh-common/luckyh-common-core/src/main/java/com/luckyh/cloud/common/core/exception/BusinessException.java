package com.luckyh.cloud.common.core.exception;

/**
 * 可向客户端展示消息的业务异常。
 *
 * @author Lucky
 * @since 2026-10-02
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
