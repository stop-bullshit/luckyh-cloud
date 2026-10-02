package com.luckyh.cloud.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品库存。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
@TableName("inventory")
public class Inventory {

    /** 商品ID。 */
    // 逻辑变动: 商品管理-20261002-01，商品编号由数据库生成。
    @TableId(value = "product_id", type = IdType.AUTO)
    private Long productId;

    /** 商品名称。 */
    private String productName;

    /** 商品单价。 */
    private BigDecimal productPrice;

    /** 可用库存数量。 */
    private Integer availableQuantity;

    /** 库存更新时间。 */
    private LocalDateTime updateTime;
}
