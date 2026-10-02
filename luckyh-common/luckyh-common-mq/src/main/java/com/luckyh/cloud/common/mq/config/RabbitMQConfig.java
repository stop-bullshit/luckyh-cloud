package com.luckyh.cloud.common.mq.config;

import lombok.extern.slf4j.Slf4j;
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
 * 
 * @author Lucky
 */
@Slf4j
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQConfig {

    /**
     * 用户相关交换机
     */
    public static final String USER_EXCHANGE = "luckyh.user.exchange";
    
    /**
     * 订单相关交换机
     */
    public static final String ORDER_EXCHANGE = "luckyh.order.exchange";
    
    /**
     * 死信交换机
     */
    public static final String DEAD_LETTER_EXCHANGE = "luckyh.dead.letter.exchange";

    /**
     * 用户注册队列
     */
    public static final String USER_REGISTER_QUEUE = "luckyh.user.register.queue";
    
    /**
     * 订单创建队列
     */
    public static final String ORDER_CREATE_QUEUE = "luckyh.order.create.queue";
    
    /**
     * 订单支付队列
     */
    public static final String ORDER_PAYMENT_QUEUE = "luckyh.order.payment.queue";
    
    /**
     * 死信队列
     */
    public static final String DEAD_LETTER_QUEUE = "luckyh.dead.letter.queue";

    /**
     * 用户注册路由键
     */
    public static final String USER_REGISTER_ROUTING_KEY = "user.register";
    
    /**
     * 订单创建路由键
     */
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";
    
    /**
     * 订单支付路由键
     */
    public static final String ORDER_PAYMENT_ROUTING_KEY = "order.payment";

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
        
        // 开启确认模式
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("消息发送成功: {}", correlationData);
            } else {
                log.error("消息发送失败: {}, 原因: {}", correlationData, cause);
            }
        });
        
        // 开启退回模式
        rabbitTemplate.setReturnsCallback((returnedMessage) -> {
            log.error("消息发送失败，消息被退回: {}", returnedMessage);
        });
        
        return rabbitTemplate;
    }

    /**
     * 监听器容器工厂配置
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        
        // 设置并发消费者数量
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        
        // 设置确认模式
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        
        return factory;
    }

    // ================== 用户相关交换机和队列 ==================

    /**
     * 用户交换机
     */
    @Bean
    public TopicExchange userExchange() {
        return ExchangeBuilder.topicExchange(USER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 用户注册队列
     */
    @Bean
    public Queue userRegisterQueue() {
        return QueueBuilder.durable(USER_REGISTER_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dead.letter")
                .build();
    }

    /**
     * 用户注册队列绑定
     */
    @Bean
    public Binding userRegisterBinding() {
        return BindingBuilder.bind(userRegisterQueue())
                .to(userExchange())
                .with(USER_REGISTER_ROUTING_KEY);
    }

    // ================== 订单相关交换机和队列 ==================

    /**
     * 订单交换机
     */
    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder.topicExchange(ORDER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 订单创建队列
     */
    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(ORDER_CREATE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dead.letter")
                .build();
    }

    /**
     * 订单支付队列
     */
    @Bean
    public Queue orderPaymentQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dead.letter")
                .build();
    }

    /**
     * 订单创建队列绑定
     */
    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue())
                .to(orderExchange())
                .with(ORDER_CREATE_ROUTING_KEY);
    }

    /**
     * 订单支付队列绑定
     */
    @Bean
    public Binding orderPaymentBinding() {
        return BindingBuilder.bind(orderPaymentQueue())
                .to(orderExchange())
                .with(ORDER_PAYMENT_ROUTING_KEY);
    }

    // ================== 死信相关交换机和队列 ==================

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /**
     * 死信队列绑定
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dead.letter");
    }
}