package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.order.dto.AccountDebitRequest;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignFallbackErrorsTest {

    @Test
    void nestedTimeoutsReturn504FromFallbackFactories() {
        Result<?> user = new UserServiceFallbackFactory()
                .create(new RuntimeException("technical details", new SocketTimeoutException("read timed out")))
                .getUserById(1L);
        Result<?> inventory = new InventoryServiceFallbackFactory()
                .create(new RuntimeException("technical details", new TimeoutException("time limiter")))
                .getProductById(1L);
        assertEquals(504, user.getCode());
        assertEquals("认证用户服务请求超时", user.getMessage());
        assertEquals(504, inventory.getCode());
        assertEquals("库存服务请求超时", inventory.getMessage());
    }

    @Test
    void connectionFailureReturns503WithoutTechnicalDetails() {
        Result<?> account = new AccountServiceFallbackFactory()
                .create(new ConnectException("internal address"))
                .debit(new AccountDebitRequest(1L, BigDecimal.ONE));
        assertEquals(503, account.getCode());
        assertEquals("账户服务暂不可用", account.getMessage());
    }

    @Test
    void downstreamHttp500RemainsSafe500() {
        FeignException cause = mock(FeignException.class);
        when(cause.status()).thenReturn(500);
        Result<?> account = new AccountServiceFallbackFactory()
                .create(cause)
                .debit(new AccountDebitRequest(1L, BigDecimal.ONE));
        assertEquals(500, account.getCode());
        assertEquals("账户服务请求失败", account.getMessage());
    }
}
