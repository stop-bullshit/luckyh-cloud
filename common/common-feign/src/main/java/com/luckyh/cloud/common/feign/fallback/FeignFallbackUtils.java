package com.luckyh.cloud.common.feign.fallback;

import com.luckyh.cloud.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign降级处理工具类
 * 
 * @author Lucky
 */
@Slf4j
public class FeignFallbackUtils {

    /**
     * 创建降级响应
     */
    public static <T> R<T> createFallbackResult(String serviceName, String methodName, Throwable cause) {
        log.error("调用服务[{}]的方法[{}]失败，进入降级处理", serviceName, methodName, cause);
        return R.fail(500, "服务暂时不可用，请稍后重试");
    }

    /**
     * 创建超时降级响应
     */
    public static <T> R<T> createTimeoutFallback(String serviceName, String methodName) {
        log.warn("调用服务[{}]的方法[{}]超时，进入降级处理", serviceName, methodName);
        return R.fail(504, "服务响应超时，请稍后重试");
    }

    /**
     * 创建熔断降级响应
     */
    public static <T> R<T> createCircuitBreakerFallback(String serviceName, String methodName) {
        log.warn("调用服务[{}]的方法[{}]触发熔断，进入降级处理", serviceName, methodName);
        return R.fail(503, "服务熔断中，请稍后重试");
    }
}