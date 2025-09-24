package com.luckyh.cloud.common.trace.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * SkyWalking追踪配置
 */
@Configuration
@ConditionalOnProperty(prefix = "skywalking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkyWalkingConfig {
    
    // SkyWalking主要通过Java Agent进行配置
    // 这里主要用于条件装配和扩展配置
    
}
