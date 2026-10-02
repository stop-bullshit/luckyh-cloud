package com.luckyh.cloud.common.mq.consumer;

import com.luckyh.cloud.common.mq.message.BaseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.io.IOException;

/**
 * 消息消费者基类
 * 提供消息消费的公共方法和异常处理
 * 
 * @author Lucky
 */
@Slf4j
public abstract class BaseMessageConsumer {

    /**
     * 处理消息的抽象方法，子类必须实现
     * 
     * @param message 消息内容
     * @return 是否处理成功
     */
    protected abstract boolean processMessage(BaseMessage message);

    /**
     * 获取消费者名称，用于日志记录
     * 
     * @return 消费者名称
     */
    protected abstract String getConsumerName();

    /**
     * 消息消费处理器
     * 
     * @param message 消息内容
     * @param rawMessage 原始消息
     * @param deliveryTag 投递标签
     * @param channel 通道
     */
    protected void handleMessage(@Payload BaseMessage message,
                                Message rawMessage,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                com.rabbitmq.client.Channel channel) {
        
        String messageId = message.getMessageId();
        String consumerName = getConsumerName();
        
        log.info("[{}] 开始处理消息: MessageId={}, Message={}", consumerName, messageId, message);
        
        try {
            // 处理消息
            boolean success = processMessage(message);
            
            if (success) {
                // 手动确认消息
                channel.basicAck(deliveryTag, false);
                log.info("[{}] 消息处理成功: MessageId={}", consumerName, messageId);
            } else {
                // 处理失败，拒绝消息并重新入队
                channel.basicNack(deliveryTag, false, true);
                log.warn("[{}] 消息处理失败，重新入队: MessageId={}", consumerName, messageId);
            }
            
        } catch (Exception e) {
            log.error("[{}] 消息处理异常: MessageId={}", consumerName, messageId, e);
            
            try {
                // 获取重试次数
                Integer retryCount = (Integer) rawMessage.getMessageProperties().getHeaders().get("x-retry-count");
                if (retryCount == null) {
                    retryCount = 0;
                }
                
                // 最大重试次数
                int maxRetries = getMaxRetries();
                
                if (retryCount < maxRetries) {
                    // 增加重试次数并重新入队
                    rawMessage.getMessageProperties().getHeaders().put("x-retry-count", retryCount + 1);
                    channel.basicNack(deliveryTag, false, true);
                    log.warn("[{}] 消息处理异常，第{}次重试: MessageId={}", consumerName, retryCount + 1, messageId);
                } else {
                    // 超过最大重试次数，发送到死信队列
                    channel.basicNack(deliveryTag, false, false);
                    log.error("[{}] 消息处理失败，已达最大重试次数，发送到死信队列: MessageId={}", consumerName, messageId);
                }
                
            } catch (IOException ioException) {
                log.error("[{}] 消息确认失败: MessageId={}", consumerName, messageId, ioException);
            }
        }
    }

    /**
     * 获取最大重试次数，子类可以重写
     * 
     * @return 最大重试次数
     */
    protected int getMaxRetries() {
        return 3;
    }

    /**
     * 死信队列消息处理器
     * 
     * @param message 死信消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "dead.letter.queue", durable = "true"),
            exchange = @Exchange(name = "dead.letter.exchange", type = ExchangeTypes.DIRECT),
            key = "dead.letter"
    ))
    public void handleDeadLetterMessage(@Payload BaseMessage message) {
        log.error("[DeadLetterConsumer] 收到死信消息: MessageId={}, Message={}", 
                message.getMessageId(), message);
        
        // 这里可以实现死信消息的特殊处理逻辑
        // 比如记录到数据库、发送告警邮件等
        handleDeadLetter(message);
    }

    /**
     * 处理死信消息的方法，子类可以重写
     * 
     * @param message 死信消息
     */
    protected void handleDeadLetter(BaseMessage message) {
        // 默认只记录日志，子类可以实现具体的死信处理逻辑
        log.warn("死信消息处理: MessageId={}, 请检查消息处理逻辑", message.getMessageId());
    }
}