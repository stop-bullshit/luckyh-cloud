package com.luckyh.cloud.inventory.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品补货请求。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
public class ReplenishInventoryRequest {

    /** 保留请求精度进行整数校验，避免 JSON 小数被自动截断为整数。 */
    @NotNull(message = "补货数量不能为空")
    @DecimalMin(value = "1", message = "补货数量必须至少为1")
    @DecimalMax(value = "2147483647", message = "补货数量超过库存数量上限")
    @Digits(integer = 10, fraction = 0, message = "补货数量必须为整数")
    private BigDecimal quantity;
}
