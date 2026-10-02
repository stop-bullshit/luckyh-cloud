package com.luckyh.cloud.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthFilterTest {

    @Test
    void propagatesAuthenticationServiceFailureInsteadOfReturningUnauthorized() {
        WebClientRequestException authFailure = new WebClientRequestException(
                new ConnectException("auth-service unavailable"), HttpMethod.GET,
                URI.create("http://auth-service/auth/validate"), new HttpHeaders());
        AuthFilter filter = new AuthFilter(WebClient.builder().exchangeFunction(request -> Mono.error(authFailure)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));

        WebClientRequestException actual = assertThrows(WebClientRequestException.class,
                () -> filter.filter(exchange, ignoredExchange -> Mono.empty()).block());
        assertSame(authFailure, actual);
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void propagatesAuthenticationServiceUnavailableInsteadOfReturningUnauthorized() {
        AuthFilter filter = new AuthFilter(WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));

        assertThrows(WebClientResponseException.ServiceUnavailable.class,
                () -> filter.filter(exchange, ignoredExchange -> Mono.empty()).block());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsAnInvalidTokenReturnedByAuthenticationService() {
        AuthFilter filter = new AuthFilter(WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"code\":401}")
                        .build())));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));

        filter.filter(exchange, ignoredExchange -> Mono.empty()).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void mapsAuthenticationServiceErrorCodesToServiceStatuses() {
        assertAuthenticationServiceFailure("{\"code\":500}", HttpStatus.SERVICE_UNAVAILABLE);
        assertAuthenticationServiceFailure("{\"code\":504}", HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void rejectsEmptyOrMalformedAuthenticationResponsesAsServiceFailures() {
        assertAuthenticationServiceFailure(null, HttpStatus.SERVICE_UNAVAILABLE);
        assertAuthenticationServiceFailure("{}", HttpStatus.SERVICE_UNAVAILABLE);
        assertAuthenticationServiceFailure("{\"code\":\"401\"}", HttpStatus.SERVICE_UNAVAILABLE);
        assertAuthenticationServiceFailure("invalid-json", HttpStatus.SERVICE_UNAVAILABLE);
    }

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

    private void assertAuthenticationServiceFailure(String body, HttpStatus expectedStatus) {
        ClientResponse.Builder response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (body != null) {
            response.body(body);
        }
        AuthFilter filter = new AuthFilter(WebClient.builder().exchangeFunction(request -> Mono.just(response.build())));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> filter.filter(exchange, ignoredExchange -> Mono.empty()).block());
        assertEquals(expectedStatus, error.getStatusCode());
        assertNull(exchange.getResponse().getStatusCode());
    }
}
