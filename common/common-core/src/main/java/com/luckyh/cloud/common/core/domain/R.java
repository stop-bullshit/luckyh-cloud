package com.luckyh.cloud.common.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用返回结果类
 * 
 * @author Lucky
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private int code;

    /**
     * 返回消息
     */
    private String message;

    /**
     * 返回数据
     */
    private T data;

    /**
     * 成功状态码
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * 失败状态码
     */
    public static final int FAIL_CODE = 500;

    /**
     * 未授权状态码
     */
    public static final int UNAUTHORIZED_CODE = 401;

    /**
     * 成功返回结果
     */
    public static <T> R<T> ok() {
        return new R<>(SUCCESS_CODE, "操作成功", null);
    }

    /**
     * 成功返回结果
     */
    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS_CODE, "操作成功", data);
    }

    /**
     * 成功返回结果
     */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(SUCCESS_CODE, message, data);
    }

    /**
     * 成功返回结果 (兼容原Result.success)
     */
    public static <T> R<T> success() {
        return ok();
    }

    /**
     * 成功返回结果 (兼容原Result.success)
     */
    public static <T> R<T> success(T data) {
        return ok(data);
    }

    /**
     * 成功返回结果 (兼容原Result.success)
     */
    public static <T> R<T> success(String message, T data) {
        return ok(message, data);
    }

    /**
     * 失败返回结果
     */
    public static <T> R<T> fail() {
        return new R<>(FAIL_CODE, "操作失败", null);
    }

    /**
     * 失败返回结果
     */
    public static <T> R<T> fail(String message) {
        return new R<>(FAIL_CODE, message, null);
    }

    /**
     * 失败返回结果 (兼容原Result.error)
     */
    public static <T> R<T> error(String message) {
        return fail(message);
    }

    /**
     * 失败返回结果
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * 失败返回结果
     */
    public static <T> R<T> fail(int code, String message, T data) {
        return new R<>(code, message, data);
    }

    /**
     * 未授权返回结果
     */
    public static <T> R<T> unauthorized(String message) {
        return new R<>(UNAUTHORIZED_CODE, message, null);
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return SUCCESS_CODE == this.code;
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }

    /**
     * 获取状态码 (兼容原Result.getCode)
     */
    public Integer getCode() {
        return this.code;
    }
}