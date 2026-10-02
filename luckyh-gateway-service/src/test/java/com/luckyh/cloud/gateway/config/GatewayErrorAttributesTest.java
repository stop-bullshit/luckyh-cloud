package com.luckyh.cloud.gateway.config;

import io.netty.channel.ConnectTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayErrorAttributesTest {

    private final GatewayErrorAttributes errorAttributes = new GatewayErrorAttributes();

    @Test
    void preservesGatewayStatusesWithoutExposingTechnicalMessages() {
        assertEquals(Map.of("status", 503, "code", 503, "message", "服务暂不可用，请稍后重试"),
                attributes(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No servers available")));
        assertEquals(Map.of("status", 504, "code", 504, "message", "服务请求超时，请稍后重试"),
                attributes(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took too long")));
        assertEquals(Map.of("status", 500, "code", 500, "message", "服务处理失败，请稍后重试"),
                attributes(new IllegalStateException("internal database details")));
    }

    @Test
    void mapsConnectionAndTimeoutFailuresToServiceStatuses() {
        assertEquals(503, attributes(new RuntimeException(new ConnectException("connection refused"))).get("status"));
        assertEquals(503, attributes(new WebClientRequestException(
                new ConnectException("connection refused"), HttpMethod.GET,
                URI.create("http://auth-service/auth/validate"), new HttpHeaders()))
                .get("status"));
        assertEquals(504, attributes(new RuntimeException(new ConnectTimeoutException("connection timed out"))).get("status"));
        assertEquals(504, attributes(new RuntimeException(new SocketTimeoutException("read timed out"))).get("status"));
    }

    @Test
    void preservesAuthenticationServiceUnavailableStatus() {
        WebClientResponseException error = new WebClientResponseException(503, "No instances available",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        assertEquals(Map.of("status", 503, "code", 503, "message", "服务暂不可用，请稍后重试"),
                attributes(error));
    }

    private Map<String, Object> attributes(Throwable error) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order/orders"));
        errorAttributes.storeErrorInformation(error, exchange);
        ServerRequest request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
        return errorAttributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());
    }
}
