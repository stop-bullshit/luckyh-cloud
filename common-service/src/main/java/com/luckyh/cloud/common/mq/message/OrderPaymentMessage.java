package com.luckyh.cloud.common.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单支付消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPaymentMessage extends BaseMessage {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 支付金额
     */
    private BigDecimal paymentAmount;
    
    /**
     * 支付方式
     */
    private String paymentMethod;
    
    /**
     * 支付状态
     */
    private String paymentStatus;
    
    public OrderPaymentMessage() {
        super("ORDER_PAYMENT", "order-service");
    }
    
    public OrderPaymentMessage(Long orderId, String orderNo, Long userId, 
                              BigDecimal paymentAmount, String paymentMethod, String paymentStatus) {
        this();
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.paymentAmount = paymentAmount;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
    }
}
