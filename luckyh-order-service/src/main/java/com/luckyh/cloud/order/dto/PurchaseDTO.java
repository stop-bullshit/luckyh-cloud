package com.luckyh.cloud.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 跨服务购买请求，商品价格由库存服务提供。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
public class PurchaseDTO {

    /** 登录用户 ID。 */
    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    private Long userId;

    /** 库存商品ID。 */
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须大于0")
    private Long productId;

    /** 购买数量。 */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量必须大于0")
    private Integer quantity;
}
