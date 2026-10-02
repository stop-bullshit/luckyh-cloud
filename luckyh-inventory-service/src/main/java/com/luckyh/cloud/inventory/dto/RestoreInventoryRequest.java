package com.luckyh.cloud.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 订单退款返还库存请求.
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
public class RestoreInventoryRequest {

    /** 商品 ID。 */
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须大于0")
    private Long productId;

    /** 返还数量。 */
    @NotNull(message = "返还数量不能为空")
    @Positive(message = "返还数量必须大于0")
    private Integer quantity;
}
