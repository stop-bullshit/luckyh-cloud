package com.luckyh.cloud.common.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQConfig {

    /**
     * 消息转换器 - 使用JSON格式
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        
        // 设置发送确认
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("消息发送成功: " + correlationData);
            } else {
                System.err.println("消息发送失败: " + correlationData + ", 原因: " + cause);
            }
        });
        
        // 设置返回确认
        rabbitTemplate.setReturnsCallback(returned -> 
            System.err.println("消息返回: " + returned.getMessage())
        );
        
        return rabbitTemplate;
    }

    /**
     * 监听器容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // 设置并发消费者数量
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        // 设置手动确认
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    // ==================== 业务队列配置 ====================

    /**
     * 用户注册队列
     */
    @Bean
    public Queue userRegisterQueue() {
        return QueueBuilder.durable("user.register.queue")
                .withArgument("x-message-ttl", 60000) // 消息TTL 60秒
                .build();
    }

    /**
     * 订单创建队列
     */
    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable("order.create.queue")
                .withArgument("x-message-ttl", 300000) // 消息TTL 5分钟
                .build();
    }

    /**
     * 订单支付队列
     */
    @Bean
    public Queue orderPaymentQueue() {
        return QueueBuilder.durable("order.payment.queue")
                .withArgument("x-message-ttl", 300000) // 消息TTL 5分钟
                .build();
    }

    /**
     * 延迟队列 - 订单超时取消
     */
    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable("order.timeout.queue")
                .withArgument("x-message-ttl", 1800000) // 30分钟自动过期
                .withArgument("x-dead-letter-exchange", "order.timeout.dlx")
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange orderTimeoutDlx() {
        return new DirectExchange("order.timeout.dlx");
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue orderTimeoutDlxQueue() {
        return QueueBuilder.durable("order.timeout.dlx.queue").build();
    }

    /**
     * 业务交换机
     */
    @Bean
    public TopicExchange businessExchange() {
        return new TopicExchange("business.exchange");
    }

    /**
     * 绑定用户注册队列
     */
    @Bean
    public Binding userRegisterBinding() {
        return BindingBuilder.bind(userRegisterQueue())
                .to(businessExchange())
                .with("user.register.#");
    }

    /**
     * 绑定订单创建队列
     */
    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue())
                .to(businessExchange())
                .with("order.create.#");
    }

    /**
     * 绑定订单支付队列
     */
    @Bean
    public Binding orderPaymentBinding() {
        return BindingBuilder.bind(orderPaymentQueue())
                .to(businessExchange())
                .with("order.payment.#");
    }

    /**
     * 绑定订单超时队列
     */
    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(businessExchange())
                .with("order.timeout.#");
    }

    /**
     * 绑定死信队列
     */
    @Bean
    public Binding orderTimeoutDlxBinding() {
        return BindingBuilder.bind(orderTimeoutDlxQueue())
                .to(orderTimeoutDlx())
                .with("order.timeout.dlx");
    }
}
