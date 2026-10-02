package com.luckyh.cloud.common.redis;

import com.luckyh.cloud.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisUtilsTest {

    @Test
    void blacklistLookupFailureDoesNotAcceptToken() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.hasKey("blacklist:token:test-token"))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        RedisUtils redisUtils = new RedisUtils(template);
        ServiceException error = assertThrows(ServiceException.class,
                () -> redisUtils.isTokenInBlacklist("test-token"));

        assertEquals(503, error.getCode());
        assertEquals("认证服务暂不可用", error.getMessage());
    }
}
