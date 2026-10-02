package com.luckyh.cloud.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.inventory.dto.DeductInventoryRequest;
import com.luckyh.cloud.inventory.dto.ReplenishInventoryRequest;
import com.luckyh.cloud.inventory.dto.RestoreInventoryRequest;
import com.luckyh.cloud.inventory.dto.SaveProductRequest;
import com.luckyh.cloud.inventory.entity.Inventory;
import com.luckyh.cloud.inventory.service.InventoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品库存接口。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    /** 库存业务服务。 */
    private final InventoryService inventoryService;

    /** 逻辑变动: 商品管理-20261002-01，商品目录提供名称搜索和分页。 */
    @GetMapping
    public Result<Page<Inventory>> getProductPage(
            @RequestParam(defaultValue = "1") @Min(1) Long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Long size,
            @RequestParam(required = false) String productName) {
        return Result.success(inventoryService.getProductPage(current, size, productName));
    }

    /** 新增商品后返回自动生成的商品编号。 */
    @PostMapping
    public Result<Long> createProduct(@RequestBody @Validated SaveProductRequest request) {
        return Result.success(inventoryService.createProduct(request));
    }

    /** 编辑商品资料，库存通过补货接口维护。 */
    @PutMapping("/{productId}")
    public Result<Void> updateProduct(@PathVariable @Positive Long productId,
                                      @RequestBody @Validated SaveProductRequest request) {
        if (!inventoryService.updateProduct(productId, request)) {
            return Result.error(404, "商品不存在");
        }
        return Result.success();
    }

    /** 补货只增加库存，数据库条件更新失败时明确返回失败。 */
    @PostMapping("/{productId}/replenish")
    public Result<Void> replenish(@PathVariable @Positive Long productId,
                                  @RequestBody @Validated ReplenishInventoryRequest request) {
        if (!inventoryService.replenish(productId, request.getQuantity().intValueExact())) {
            return Result.error(409, "商品不存在或补货后库存超过上限");
        }
        return Result.success();
    }

    @GetMapping("/{productId}")
    public Result<Inventory> getInventory(@PathVariable Long productId) {
        Inventory inventory = inventoryService.getByProductId(productId);
        if (inventory == null) {
            return Result.error(404, "商品库存不存在");
        }
        return Result.success(inventory);
    }

    /**
     * 逻辑变动: 跨服务事务示例-20261002-01。
     * 订单服务根据业务失败码中止全局事务，库存服务不吞掉系统异常。
     */
    @PostMapping("/deduct")
    public Result<Void> deduct(@RequestBody @Validated DeductInventoryRequest request) {
        if (!inventoryService.deduct(request.getProductId(), request.getQuantity())) {
            return Result.error(409, "商品不存在或可用库存不足");
        }
        return Result.success();
    }

    /** 执行订单退款的全局事务库存返还分支。 */
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody @Validated RestoreInventoryRequest request) {
        if (!inventoryService.restore(request.getProductId(), request.getQuantity())) {
            return Result.error(409, "商品不存在或返还后库存超过上限");
        }
        return Result.success();
    }
}
