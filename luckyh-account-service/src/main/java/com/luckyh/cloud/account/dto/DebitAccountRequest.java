package com.luckyh.cloud.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 全局事务中的账户扣款请求。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
public class DebitAccountRequest {

    /** 需要扣款的业务用户 ID。 */
    @NotNull(message = "用户 ID 不能为空")
    @Positive(message = "用户 ID 必须大于 0")
    private Long userId;

    /** 扣款金额，单位为元，最多两位小数。 */
    @NotNull(message = "扣款金额不能为空")
    @DecimalMin(value = "0.01", message = "扣款金额不能小于 0.01 元")
    @Digits(integer = 16, fraction = 2, message = "扣款金额最多 16 位整数和 2 位小数")
    private BigDecimal amount;
}
