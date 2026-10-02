package com.luckyh.cloud.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户账户余额。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
@TableName("account_balance")
public class AccountBalance {

    /** 账户所属业务用户 ID。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 可用余额，单位为元。 */
    private BigDecimal balance;

    /** 最近一次余额更新时间。 */
    private LocalDateTime updateTime;
}
