package com.luckyh.cloud.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyh.cloud.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 库存数据访问。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 逻辑变动: 商品管理-20261002-01。
     * 编辑商品仅修改资料，避免覆盖同时发生的库存扣减或补货。
     */
    @Update("""
            UPDATE inventory
            SET product_name = #{productName}, product_price = #{productPrice}, update_time = CURRENT_TIMESTAMP
            WHERE product_id = #{productId}
            """)
    int updateProduct(@Param("productId") Long productId, @Param("productName") String productName,
                      @Param("productPrice") BigDecimal productPrice);

    /**
     * 逻辑变动: 商品管理-20261002-01。
     * 在同一条更新中累加并限制库存上限，避免并发丢失补货和整数溢出。
     */
    @Update("""
            UPDATE inventory
            SET available_quantity = available_quantity + #{quantity}, update_time = CURRENT_TIMESTAMP
            WHERE product_id = #{productId} AND available_quantity <= 2147483647 - #{quantity}
            """)
    int replenish(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 逻辑变动: 跨服务事务示例-20261002-01。
     * 条件更新在数据库内完成数量判断和扣减，避免并发请求超扣。
     */
    @Update("""
            UPDATE inventory
            SET available_quantity = available_quantity - #{quantity}, update_time = CURRENT_TIMESTAMP
            WHERE product_id = #{productId} AND available_quantity >= #{quantity}
            """)
    int deduct(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
