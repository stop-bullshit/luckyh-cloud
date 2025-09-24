package com.luckyh.cloud.common.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单支付消息
 * 
 * @author Lucky
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPaymentMessage extends BaseMessage {

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
     * 支付金额
     */
    private BigDecimal paymentAmount;

    /**
     * 支付方式 (1: 支付宝, 2: 微信, 3: 银行卡)
     */
    private Integer paymentMethod;

    /**
     * 支付状态 (1: 支付中, 2: 支付成功, 3: 支付失败)
     */
    private Integer paymentStatus;

    /**
     * 支付流水号
     */
    private String paymentSn;

    /**
     * 支付时间
     */
    private LocalDateTime paymentTime;
}