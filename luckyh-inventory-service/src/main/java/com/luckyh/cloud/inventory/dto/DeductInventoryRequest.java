package com.luckyh.cloud.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 扣减库存请求。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
public class DeductInventoryRequest {

    /** 商品ID。 */
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须大于0")
    private Long productId;

    /** 扣减数量。 */
    @NotNull(message = "扣减数量不能为空")
    @Min(value = 1, message = "扣减数量必须至少为1")
    private Integer quantity;
}
