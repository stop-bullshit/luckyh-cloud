package com.luckyh.cloud.common.trace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import jakarta.annotation.PostConstruct;

/**
 * SkyWalking配置类
 * 
 * @author luckyh
 */
@Slf4j
@Configuration
@ConditionalOnClass(TraceContext.class)
public class SkyWalkingConfiguration {

    @PostConstruct
    public void init() {
        try {
            // 检查SkyWalking是否正常工作
            String traceId = TraceContext.traceId();
            if (traceId != null && !traceId.isEmpty()) {
                log.info("SkyWalking初始化成功，当前TraceId: {}", traceId);
            } else {
                log.warn("SkyWalking Agent可能未启动，建议检查skywalking-agent配置");
            }
        } catch (Exception e) {
            log.warn("SkyWalking初始化检查失败: {}，建议检查依赖配置", e.getMessage());
        }
    }

    /**
     * SkyWalking健康检查Bean
     */
    @Bean
    public SkyWalkingHealthIndicator skyWalkingHealthIndicator() {
        return new SkyWalkingHealthIndicator();
    }

    /**
     * SkyWalking健康检查指示器
     */
    public static class SkyWalkingHealthIndicator {

        public boolean isHealthy() {
            try {
                String traceId = TraceContext.traceId();
                return traceId != null && !traceId.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }

        public String getStatus() {
            return isHealthy() ? "UP" : "DOWN - Agent not running";
        }
    }
}