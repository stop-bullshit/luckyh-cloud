package com.luckyh.cloud.gateway.config;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * 网关异常响应，向前端提供安全的错误说明。
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable error = getError(request);
        int status = (int) super.getErrorAttributes(request, options).get("status");

        if (status == 500 && error instanceof WebClientResponseException responseException) {
            status = responseException.getStatusCode().value();
        }

        // 逻辑变动: 保留已有HTTP状态，并将连接与超时故障映射为可识别状态-20261002-2119-01
        if (status == 500 && !(error instanceof ResponseStatusException)) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectTimeoutException
                        || cause instanceof SocketTimeoutException
                        || cause instanceof java.util.concurrent.TimeoutException
                        || cause instanceof TimeoutException) {
                    status = HttpStatus.GATEWAY_TIMEOUT.value();
                    break;
                }
                if (cause instanceof ConnectException
                        || cause instanceof UnknownHostException
                        || cause instanceof WebClientRequestException) {
                    status = HttpStatus.SERVICE_UNAVAILABLE.value();
                }
            }
        }

        String message = switch (status) {
            case 503 -> "服务暂不可用，请稍后重试";
            case 504 -> "服务请求超时，请稍后重试";
            case 500 -> "服务处理失败，请稍后重试";
            default -> "请求失败";
        };
        return Map.of("status", status, "code", status, "message", message);
    }
}
