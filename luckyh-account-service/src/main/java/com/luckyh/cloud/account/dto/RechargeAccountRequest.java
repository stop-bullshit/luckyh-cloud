package com.luckyh.cloud.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户充值请求。
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
public class RechargeAccountRequest {

    /** 本次增加的余额，单位为元。 */
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额不能小于 0.01 元")
    @Digits(integer = 16, fraction = 2, message = "充值金额最多 16 位整数和 2 位小数")
    private BigDecimal amount;
}
