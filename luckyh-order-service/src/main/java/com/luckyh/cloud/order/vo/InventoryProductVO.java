package com.luckyh.cloud.order.vo;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 库存服务提供的商品资料。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
public class InventoryProductVO {

    /** 库存商品ID。 */
    private Long productId;

    /** 商品名称。 */
    private String productName;

    /** 服务端商品单价。 */
    private BigDecimal productPrice;
}
