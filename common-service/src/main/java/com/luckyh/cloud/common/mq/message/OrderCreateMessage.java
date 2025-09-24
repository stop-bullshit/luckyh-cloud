package com.luckyh.cloud.common.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单创建消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreateMessage extends BaseMessage {
    
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
     * 商品名称
     */
    private String productName;
    
    /**
     * 数量
     */
    private Integer quantity;
    
    /**
     * 单价
     */
    private BigDecimal price;
    
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    
    public OrderCreateMessage() {
        super("ORDER_CREATE", "order-service");
    }
    
    public OrderCreateMessage(Long orderId, String orderNo, Long userId, String productName, 
                             Integer quantity, BigDecimal price, BigDecimal totalAmount) {
        this();
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = totalAmount;
    }
}
