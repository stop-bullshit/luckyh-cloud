package com.luckyh.cloud.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存返还分支请求.
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRestoreRequest {

    /** 商品 ID。 */
    private Long productId;

    /** 返还数量。 */
    private Integer quantity;
}
