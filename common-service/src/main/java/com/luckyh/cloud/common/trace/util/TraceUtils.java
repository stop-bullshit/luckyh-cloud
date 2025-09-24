package com.luckyh.cloud.common.trace.util;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import lombok.extern.slf4j.Slf4j;

/**
 * 链路追踪工具类
 */
@Slf4j
public class TraceUtils {

    /**
     * 获取当前追踪ID
     */
    public static String getTraceId() {
        try {
            return TraceContext.traceId();
        } catch (Exception e) {
            log.warn("获取TraceId失败，可能SkyWalking Agent未启动: {}", e.getMessage());
            return "NO-TRACE-" + System.currentTimeMillis();
        }
    }

    /**
     * 添加追踪标签
     */
    @Trace
    public static void addTag(String key, String value) {
        try {
            TraceContext.putCorrelation(key, value);
        } catch (Exception e) {
            log.warn("添加追踪标签失败: {}", e.getMessage());
        }
    }

    /**
     * 获取追踪标签
     */
    public static String getTag(String key) {
        try {
            return TraceContext.getCorrelation(key).orElse("");
        } catch (Exception e) {
            log.warn("获取追踪标签失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 记录业务事件
     */
    @Trace
    public static void recordEvent(String eventName, String details) {
        try {
            addTag("event", eventName);
            addTag("details", details);
            log.info("记录业务事件: {} - {}", eventName, details);
        } catch (Exception e) {
            log.warn("记录业务事件失败: {}", e.getMessage());
        }
    }

    /**
     * 记录用户操作
     */
    @Trace
    public static void recordUserAction(String userId, String action) {
        try {
            addTag("userId", userId);
            addTag("action", action);
            log.info("记录用户操作: 用户{} 执行 {}", userId, action);
        } catch (Exception e) {
            log.warn("记录用户操作失败: {}", e.getMessage());
        }
    }

    /**
     * 记录业务异常
     */
    @Trace
    public static void recordBusinessException(String errorCode, String errorMessage) {
        try {
            addTag("errorCode", errorCode);
            addTag("errorMessage", errorMessage);
            addTag("exceptionType", "business");
            log.error("业务异常: {} - {}", errorCode, errorMessage);
        } catch (Exception e) {
            log.warn("记录业务异常失败: {}", e.getMessage());
        }
    }

    /**
     * 检查SkyWalking是否可用
     */
    public static boolean isSkyWalkingAvailable() {
        try {
            String traceId = TraceContext.traceId();
            return traceId != null && !traceId.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
