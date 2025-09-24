package com.luckyh.cloud.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    
    // 成功
    SUCCESS(200, "success"),
    
    // 客户端错误
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    VALIDATION_ERROR(422, "参数校验失败"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    
    // 服务器错误
    ERROR(500, "系统内部错误"),
    SYSTEM_ERROR(500, "系统异常"),
    BUSINESS_ERROR(500, "业务异常"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    GATEWAY_TIMEOUT(504, "网关超时"),
    
    // 业务错误码 (6000-6999)
    USER_NOT_FOUND(6001, "用户不存在"),
    USER_ALREADY_EXISTS(6002, "用户已存在"),
    PASSWORD_ERROR(6003, "密码错误"),
    TOKEN_INVALID(6004, "令牌无效"),
    TOKEN_EXPIRED(6005, "令牌已过期"),
    INSUFFICIENT_PERMISSIONS(6006, "权限不足"),
    
    ORDER_NOT_FOUND(6101, "订单不存在"),
    ORDER_STATUS_ERROR(6102, "订单状态错误"),
    PAYMENT_FAILED(6103, "支付失败"),
    INVENTORY_INSUFFICIENT(6104, "库存不足");
    
    private final Integer code;
    private final String message;
}
