package com.luckyh.cloud.common.mq.producer;

import com.luckyh.cloud.common.mq.message.BaseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(RabbitTemplate.class)
public class MessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送消息到指定交换机和路由键
     */
    public void sendMessage(String exchange, String routingKey, BaseMessage message) {
        try {
            // 设置消息ID
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }

            log.info("发送消息: exchange={}, routingKey={}, messageId={}, messageType={}",
                    exchange, routingKey, message.getMessageId(), message.getMessageType());

            rabbitTemplate.convertAndSend(exchange, routingKey, message);

        } catch (Exception e) {
            log.error("发送消息失败: exchange={}, routingKey={}, message={}", exchange, routingKey, message, e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发送用户注册消息
     */
    public void sendUserRegisterMessage(BaseMessage message) {
        sendMessage("business.exchange", "user.register.event", message);
    }

    /**
     * 发送订单创建消息
     */
    public void sendOrderCreateMessage(BaseMessage message) {
        sendMessage("business.exchange", "order.create.event", message);
    }

    /**
     * 发送订单支付消息
     */
    public void sendOrderPaymentMessage(BaseMessage message) {
        sendMessage("business.exchange", "order.payment.event", message);
    }

    /**
     * 发送延迟消息 - 订单超时取消
     */
    public void sendOrderTimeoutMessage(BaseMessage message, long delayMillis) {
        try {
            log.info("发送延迟消息: messageId={}, delayMillis={}", message.getMessageId(), delayMillis);

            rabbitTemplate.convertAndSend("business.exchange", "order.timeout.event", message, msg -> {
                // 使用x-delay头设置延迟时间（需要rabbitmq-delayed-message-exchange插件）
                msg.getMessageProperties().setHeader("x-delay", delayMillis);
                return msg;
            });

        } catch (Exception e) {
            log.error("发送延迟消息失败: message={}, delay={}", message, delayMillis, e);
            throw new RuntimeException("延迟消息发送失败", e);
        }
    }
}
