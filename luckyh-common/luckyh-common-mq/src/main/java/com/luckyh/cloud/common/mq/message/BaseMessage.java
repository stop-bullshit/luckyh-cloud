package com.luckyh.cloud.common.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息基类
 * 
 * @author Lucky
 */
@Data
public abstract class BaseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 消息时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 消息来源服务
     */
    private String sourceService;

    /**
     * 消息版本
     */
    private String version = "1.0";

    /**
     * 重试次数
     */
    private Integer retryCount = 0;

    public BaseMessage() {
        this.timestamp = LocalDateTime.now();
        this.messageId = java.util.UUID.randomUUID().toString();
    }
}