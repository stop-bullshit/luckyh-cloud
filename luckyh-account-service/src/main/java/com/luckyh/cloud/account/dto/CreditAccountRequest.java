package com.luckyh.cloud.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 全局事务中的账户退款请求.
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
public class CreditAccountRequest {

    /** 收款用户 ID。 */
    @NotNull(message = "用户 ID 不能为空")
    @Positive(message = "用户 ID 必须大于 0")
    private Long userId;

    /** 退款金额，单位为元。 */
    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0.01", message = "退款金额不能小于 0.01 元")
    @Digits(integer = 16, fraction = 2, message = "退款金额最多 16 位整数和 2 位小数")
    private BigDecimal amount;

    /** 可选订单号，用于在余额明细中追溯订单退款。 */
    @Size(max = 64, message = "订单号最多 64 个字符")
    private String orderNo;
}
