package com.luckyh.cloud.common.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础消息类
 */
@Data
public class BaseMessage implements Serializable {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 发送方服务名
     */
    private String sender;

    /**
     * 重试次数
     */
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount = 3;

    /**
     * 链路追踪ID
     */
    private String traceId;

    public BaseMessage() {
        this.sendTime = LocalDateTime.now();
    }

    public BaseMessage(String messageType, String sender) {
        this();
        this.messageType = messageType;
        this.sender = sender;
    }
}
