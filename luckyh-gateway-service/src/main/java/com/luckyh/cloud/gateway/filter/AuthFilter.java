package com.luckyh.cloud.gateway.filter;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * JWT认证过滤器
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    // 不需要认证的路径
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/actuator",
            "/api/auth/health");

    public AuthFilter(@LoadBalanced WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("http://auth-service")
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 检查是否需要跳过认证
        if (shouldSkipAuth(path)) {
            return chain.filter(exchange);
        }

        // 获取Authorization头
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StrUtil.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            return handleUnauthorized(exchange, "缺少认证令牌");
        }

        // 验证令牌
        return validateToken(authHeader)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return handleUnauthorized(exchange, "认证令牌无效");
                    }
                    // 逻辑变动: 认证通过后保留下游服务异常的原始状态-20261002-2027-01
                    return chain.filter(exchange);
                });
    }

    /**
     * 检查是否应该跳过认证
     */
    private boolean shouldSkipAuth(String path) {
        return EXCLUDE_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 验证令牌
     */
    private Mono<Boolean> validateToken(String authHeader) {
        return webClient.get()
                .uri("/auth/validate")
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorMap(error -> error instanceof DecodingException
                                || error instanceof UnsupportedMediaTypeException,
                        error -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "认证服务响应异常", error))
                // 逻辑变动: 仅明确的401表示令牌无效，其余异常响应按服务故障处理-20261002-2126-01
                .map(response -> {
                    JsonNode codeNode = response.get("code");
                    if (codeNode == null || !codeNode.isIntegralNumber() || !codeNode.canConvertToInt()) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "认证服务响应异常");
                    }
                    int code = codeNode.intValue();
                    if (code == 200) {
                        return true;
                    }
                    if (code == 401) {
                        return false;
                    }
                    HttpStatus status = code == 504 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
                    throw new ResponseStatusException(status, "认证服务响应异常");
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "认证服务响应为空")));
    }

    /**
     * 处理未授权响应
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        String body = String.format("{\"code\":401,\"message\":\"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -50; // 在全局日志过滤器之后执行
    }
}
