package com.luckyh.cloud.common.mq.consumer;

import com.luckyh.cloud.common.mq.message.BaseMessage;
import com.luckyh.cloud.common.mq.message.OrderCreateMessage;
import com.luckyh.cloud.common.mq.message.OrderPaymentMessage;
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
 * 订单消息消费者示例
 * 
 * @author Lucky
 */
@Slf4j
@Component
public class OrderMessageConsumer extends BaseMessageConsumer {

    /**
     * 消费订单创建消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "order.create.queue", durable = "true"),
            exchange = @Exchange(name = "order.direct.exchange", type = ExchangeTypes.DIRECT),
            key = "order.create"
    ))
    public void handleOrderCreateMessage(@Payload OrderCreateMessage message,
                                       Message rawMessage,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                       Channel channel) {
        handleMessage(message, rawMessage, deliveryTag, channel);
    }

    /**
     * 消费订单支付消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "order.payment.queue", durable = "true"),
            exchange = @Exchange(name = "order.direct.exchange", type = ExchangeTypes.DIRECT),
            key = "order.payment"
    ))
    public void handleOrderPaymentMessage(@Payload OrderPaymentMessage message,
                                        Message rawMessage,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                        Channel channel) {
        handleMessage(message, rawMessage, deliveryTag, channel);
    }

    @Override
    protected boolean processMessage(BaseMessage message) {
        if (message instanceof OrderCreateMessage) {
            return processOrderCreate((OrderCreateMessage) message);
        } else if (message instanceof OrderPaymentMessage) {
            return processOrderPayment((OrderPaymentMessage) message);
        }
        
        log.warn("不支持的消息类型: {}", message.getClass().getSimpleName());
        return false;
    }

    @Override
    protected String getConsumerName() {
        return "OrderMessageConsumer";
    }

    /**
     * 处理订单创建消息的具体业务逻辑
     * 
     * @param message 订单创建消息
     * @return 处理结果
     */
    private boolean processOrderCreate(OrderCreateMessage message) {
        try {
            log.info("处理订单创建消息: OrderId={}, OrderNo={}, UserId={}, TotalAmount={}", 
                    message.getOrderId(), message.getOrderNo(), message.getUserId(), message.getTotalAmount());
            
            // 这里可以实现具体的业务逻辑，比如：
            // 1. 库存扣减
            // 2. 发送订单确认邮件
            // 3. 生成物流信息
            // 4. 更新用户积分
            // 5. 风控检查
            
            // 模拟业务处理
            Thread.sleep(200);
            
            log.info("订单创建消息处理完成: OrderId={}", message.getOrderId());
            return true;
            
        } catch (Exception e) {
            log.error("订单创建消息处理失败: OrderId={}", message.getOrderId(), e);
            return false;
        }
    }

    /**
     * 处理订单支付消息的具体业务逻辑
     * 
     * @param message 订单支付消息
     * @return 处理结果
     */
    private boolean processOrderPayment(OrderPaymentMessage message) {
        try {
            log.info("处理订单支付消息: OrderId={}, OrderNo={}, PaymentAmount={}, PaymentStatus={}", 
                    message.getOrderId(), message.getOrderNo(), message.getPaymentAmount(), message.getPaymentStatus());
            
            // 这里可以实现具体的业务逻辑，比如：
            // 1. 更新订单状态
            // 2. 发送支付成功通知
            // 3. 触发发货流程
            // 4. 更新财务记录
            // 5. 生成发票
            
            // 模拟业务处理
            Thread.sleep(150);
            
            log.info("订单支付消息处理完成: OrderId={}", message.getOrderId());
            return true;
            
        } catch (Exception e) {
            log.error("订单支付消息处理失败: OrderId={}", message.getOrderId(), e);
            return false;
        }
    }
}