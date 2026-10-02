package com.luckyh.cloud.common.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单创建消息
 * 
 * @author Lucky
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreateMessage extends BaseMessage {

    private static final long serialVersionUID = 1L;

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
     * 商品数量
     */
    private Integer quantity;

    /**
     * 商品单价
     */
    private BigDecimal price;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}