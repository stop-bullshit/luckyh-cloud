package com.luckyh.cloud.auth.config;

import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证服务的 Redis 客户端协议配置。
 *
 * @since 2026-10-02
 */
@Configuration
public class RedisClientConfig {

    // 逻辑变动: 保留单机或集群客户端选项，仅设置代理兼容的 RESP2 协议-20261002-1938-01
    @Bean
    public LettuceClientOptionsBuilderCustomizer redisProtocolVersion() {
        return builder -> builder.protocolVersion(ProtocolVersion.RESP2);
    }
}
