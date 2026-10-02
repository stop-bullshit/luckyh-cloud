package com.luckyh.cloud.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态操作流水.
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
@TableName("order_operation_log")
public class OrderOperationLog {

    /** 流水 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 订单 ID。 */
    private Long orderId;
    /** 订单编号。 */
    private String orderNo;
    /** 操作类型。 */
    private String operationType;
    /** 操作前状态，创建时为空。 */
    private Integer fromStatus;
    /** 操作后状态。 */
    private Integer toStatus;
    /** Seata 全局事务 ID。 */
    private String xid;
    /** 操作时间。 */
    private LocalDateTime createTime;
}
