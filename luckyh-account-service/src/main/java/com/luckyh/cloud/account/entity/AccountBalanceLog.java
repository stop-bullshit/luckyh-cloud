package com.luckyh.cloud.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户余额变动明细.
 *
 * @author heng.wang
 * @since 2026-10-04
 */
@Data
@TableName("account_balance_log")
public class AccountBalanceLog {

    /** 明细 ID，按倒序查询展示最近变动。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 账户所属用户 ID。 */
    private Long userId;

    /** 变动类型：RECHARGE、DEDUCT、DEBIT 或 CREDIT。 */
    private String changeType;

    /** 实际余额变动额，收入为正，支出为负。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal changeAmount;

    /** 本次变动前的余额。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal beforeBalance;

    /** 本次变动后的余额。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal afterBalance;

    /** 订单扣款或退款对应的订单号，手工变动可为空。 */
    private String orderNo;

    /** Seata 全局事务 ID，普通本地事务可为空。 */
    private String transactionId;

    /** 本次余额变动时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createTime;
}
