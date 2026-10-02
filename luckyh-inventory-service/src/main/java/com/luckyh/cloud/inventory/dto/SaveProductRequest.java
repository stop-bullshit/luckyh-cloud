package com.luckyh.cloud.inventory.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品新增与编辑请求，库存通过补货单独增加。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
public class SaveProductRequest {

    /** 商品名称。 */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称不能超过128个字符")
    private String productName;

    /** 商品单价，与订单金额的两位小数精度保持一致。 */
    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格不能小于0.01")
    @DecimalMax(value = "99999999.99", message = "商品价格不能超过99999999.99")
    @Digits(integer = 8, fraction = 2, message = "商品价格最多8位整数和2位小数")
    private BigDecimal productPrice;
}
