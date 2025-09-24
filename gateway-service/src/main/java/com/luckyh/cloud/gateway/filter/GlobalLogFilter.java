package com.luckyh.cloud.gateway.filter;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器 - 添加请求ID和日志
 */
@Slf4j
@Component
public class GlobalLogFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = IdUtil.simpleUUID();

        // 添加请求ID到请求头
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID, requestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        long startTime = System.currentTimeMillis();

        log.info("请求开始 - RequestId: {}, Method: {}, URI: {}, RemoteAddr: {}",
                requestId, request.getMethod(), request.getURI(), request.getRemoteAddress());

        return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
            long endTime = System.currentTimeMillis();
            log.info("请求结束 - RequestId: {}, 耗时: {}ms", requestId, endTime - startTime);
        }));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
