package com.luckyh.cloud.common.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luckyh.cloud.common.mq.message.BaseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 消息生产者工具类
 * 
 * @author Lucky
 */
@Slf4j
@Component
public class MessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发送用户注册消息
     * 
     * @param message 消息内容
     */
    public void sendUserRegisterMessage(BaseMessage message) {
        sendMessage("user.topic.exchange", "user.register", message);
    }

    /**
     * 发送订单创建消息
     * 
     * @param message 消息内容
     */
    public void sendOrderCreateMessage(BaseMessage message) {
        sendMessage("order.direct.exchange", "order.create", message);
    }

    /**
     * 发送订单支付消息
     * 
     * @param message 消息内容
     */
    public void sendOrderPaymentMessage(BaseMessage message) {
        sendMessage("order.direct.exchange", "order.payment", message);
    }

    /**
     * 通用消息发送方法
     * 
     * @param exchange 交换机名称
     * @param routingKey 路由键
     * @param message 消息内容
     */
    public void sendMessage(String exchange, String routingKey, BaseMessage message) {
        try {
            // 设置消息ID和发送时间
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }
            if (message.getTimestamp() == null) {
                message.setTimestamp(LocalDateTime.now());
            }

            // 生成关联数据用于消息确认
            CorrelationData correlationData = new CorrelationData(message.getMessageId());

            // 发送消息
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);

            log.info("消息发送成功 - Exchange: {}, RoutingKey: {}, MessageId: {}", 
                    exchange, routingKey, message.getMessageId());

        } catch (Exception e) {
            log.error("消息发送失败 - Exchange: {}, RoutingKey: {}, Message: {}", 
                    exchange, routingKey, message, e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发送延时消息
     * 
     * @param exchange 交换机名称
     * @param routingKey 路由键
     * @param message 消息内容
     * @param delayMillis 延时时间(毫秒)
     */
    public void sendDelayMessage(String exchange, String routingKey, BaseMessage message, long delayMillis) {
        try {
            // 设置消息ID和发送时间
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }
            if (message.getTimestamp() == null) {
                message.setTimestamp(LocalDateTime.now());
            }

            // 生成关联数据用于消息确认
            CorrelationData correlationData = new CorrelationData(message.getMessageId());

            // 发送延时消息 (需要RabbitMQ延时插件支持)
            rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
                msg.getMessageProperties().setHeader("x-delay", delayMillis);
                return msg;
            }, correlationData);

            log.info("延时消息发送成功 - Exchange: {}, RoutingKey: {}, MessageId: {}, Delay: {}ms", 
                    exchange, routingKey, message.getMessageId(), delayMillis);

        } catch (Exception e) {
            log.error("延时消息发送失败 - Exchange: {}, RoutingKey: {}, Message: {}, Delay: {}ms", 
                    exchange, routingKey, message, delayMillis, e);
            throw new RuntimeException("延时消息发送失败", e);
        }
    }
}