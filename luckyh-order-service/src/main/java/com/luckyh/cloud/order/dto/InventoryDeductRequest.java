package com.luckyh.cloud.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存服务扣减参数。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductRequest {

    /** 库存商品ID。 */
    private Long productId;

    /** 扣减数量。 */
    private Integer quantity;
}
