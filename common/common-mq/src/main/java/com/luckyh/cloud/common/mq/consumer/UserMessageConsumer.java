package com.luckyh.cloud.common.mq.consumer;

import com.luckyh.cloud.common.mq.message.BaseMessage;
import com.luckyh.cloud.common.mq.message.UserRegisterMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 用户消息消费者示例
 * 
 * @author Lucky
 */
@Slf4j
@Component
public class UserMessageConsumer extends BaseMessageConsumer {

    /**
     * 消费用户注册消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "user.register.queue", durable = "true"),
            exchange = @Exchange(name = "user.topic.exchange", type = ExchangeTypes.TOPIC),
            key = "user.register"
    ))
    public void handleUserRegisterMessage(@Payload UserRegisterMessage message,
                                        Message rawMessage,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                        Channel channel) {
        handleMessage(message, rawMessage, deliveryTag, channel);
    }

    @Override
    protected boolean processMessage(BaseMessage message) {
        if (message instanceof UserRegisterMessage) {
            UserRegisterMessage userRegisterMessage = (UserRegisterMessage) message;
            return processUserRegister(userRegisterMessage);
        }
        
        log.warn("不支持的消息类型: {}", message.getClass().getSimpleName());
        return false;
    }

    @Override
    protected String getConsumerName() {
        return "UserMessageConsumer";
    }

    /**
     * 处理用户注册消息的具体业务逻辑
     * 
     * @param message 用户注册消息
     * @return 处理结果
     */
    private boolean processUserRegister(UserRegisterMessage message) {
        try {
            log.info("处理用户注册消息: UserId={}, Username={}, Email={}", 
                    message.getUserId(), message.getUsername(), message.getEmail());
            
            // 这里可以实现具体的业务逻辑，比如：
            // 1. 发送欢迎邮件
            // 2. 初始化用户配置
            // 3. 发送短信通知
            // 4. 记录用户行为日志
            // 5. 同步到其他系统
            
            // 模拟业务处理
            Thread.sleep(100);
            
            log.info("用户注册消息处理完成: UserId={}", message.getUserId());
            return true;
            
        } catch (Exception e) {
            log.error("用户注册消息处理失败: UserId={}", message.getUserId(), e);
            return false;
        }
    }
}