package com.luckyh.cloud.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单VO
 */
@Data
public class OrderVO {

    /**
     * 订单ID
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户信息
     */
    private UserInfo userInfo;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 商品价格
     */
    private BigDecimal productPrice;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态：0-待支付，1-已支付，2-已取消，3-已退款
     */
    private Integer status;

    /**
     * 订单状态描述
     */
    private String statusDesc;

    /** 支付时间。 */
    private LocalDateTime payTime;

    /** 取消时间。 */
    private LocalDateTime cancelTime;

    /** 退款时间。 */
    private LocalDateTime refundTime;

    /** 订单状态操作流水。 */
    private List<OrderOperation> operationLogs;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 用户信息内部类
     */
    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private String email;
        private String phone;
    }

    /** 订单操作流水。 */
    @Data
    public static class OrderOperation {
        /** 操作类型。 */
        private String operationType;
        /** 操作前状态。 */
        private Integer fromStatus;
        /** 操作后状态。 */
        private Integer toStatus;
        /** 全局事务 ID。 */
        private String xid;
        /** 操作时间。 */
        private LocalDateTime createTime;
    }
}
