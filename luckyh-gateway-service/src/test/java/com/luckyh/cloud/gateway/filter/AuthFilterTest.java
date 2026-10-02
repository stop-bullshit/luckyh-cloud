package com.luckyh.cloud.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthFilterTest {

    @Test
    void propagatesDownstreamFailureAfterSuccessfulAuthentication() {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"code\":200}")
                        .build()));
        AuthFilter filter = new AuthFilter(webClientBuilder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));
        IllegalStateException downstreamFailure = new IllegalStateException("order-service unavailable");
        GatewayFilterChain chain = ignoredExchange -> Mono.error(downstreamFailure);

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> filter.filter(exchange, chain).block());
        assertSame(downstreamFailure, actual);
        assertNull(exchange.getResponse().getStatusCode());
    }
}
