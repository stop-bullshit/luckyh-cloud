package com.luckyh.cloud.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luckyh.cloud.inventory.dto.SaveProductRequest;
import com.luckyh.cloud.inventory.entity.Inventory;
import com.luckyh.cloud.inventory.mapper.InventoryMapper;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品管理、补货与全局事务内扣减。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    /** 库存数据访问。 */
    private final InventoryMapper inventoryMapper;

    /** 商品名称包含查询，并按商品编号稳定分页。 */
    public Page<Inventory> getProductPage(Long current, Long size, String productName) {
        // 逻辑变动: 商品管理-20261002-01。
        LambdaQueryWrapper<Inventory> query = new LambdaQueryWrapper<>();
        query.like(productName != null && !productName.isBlank(), Inventory::getProductName, productName);
        query.orderByAsc(Inventory::getProductId);
        return inventoryMapper.selectPage(new Page<>(current, size), query);
    }

    /** 新商品初始库存为零，库存需通过补货增加。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(SaveProductRequest request) {
        // 逻辑变动: 商品管理-20261002-01。
        Inventory inventory = new Inventory();
        inventory.setProductName(request.getProductName());
        inventory.setProductPrice(request.getProductPrice());
        inventory.setAvailableQuantity(0);
        if (inventoryMapper.insert(inventory) != 1) {
            throw new IllegalStateException("商品创建失败");
        }
        return inventory.getProductId();
    }

    /** 本地修改商品资料时检查全局锁，避免购买回滚覆盖编辑结果。 */
    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProduct(Long productId, SaveProductRequest request) {
        // 逻辑变动: 商品管理-20261002-01。
        return inventoryMapper.updateProduct(productId, request.getProductName(), request.getProductPrice()) == 1;
    }

    /** 本地补货检查全局锁，避免购买回滚覆盖补货结果。 */
    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public boolean replenish(Long productId, Integer quantity) {
        // 逻辑变动: 商品管理-20261002-01。
        return inventoryMapper.replenish(productId, quantity) == 1;
    }

    public Inventory getByProductId(Long productId) {
        return inventoryMapper.selectById(productId);
    }

    /**
     * 逻辑变动: 跨服务事务示例-20261002-01。
     * 扣减必须参与调用方的全局事务，避免库存独立提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(Long productId, Integer quantity) {
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("库存扣减必须加入全局事务");
        }

        int updatedRows = inventoryMapper.deduct(productId, quantity);
        log.info("method=deduct XID={} productId={} quantity={} rows={}", xid, productId, quantity, updatedRows);
        return updatedRows == 1;
    }

    /** 在订单退款全局事务内原子返还库存。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean restore(Long productId, Integer quantity) {
        // 逻辑变动: 已支付订单分布式退款-20261002-1730-01
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("库存返还必须加入全局事务");
        }
        int updatedRows = inventoryMapper.replenish(productId, quantity);
        log.info("method=restore XID={} productId={} quantity={} rows={}", xid, productId, quantity, updatedRows);
        return updatedRows == 1;
    }
}
