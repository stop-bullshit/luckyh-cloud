package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.order.dto.InventoryDeductRequest;
import com.luckyh.cloud.order.dto.InventoryRestoreRequest;
import com.luckyh.cloud.order.vo.InventoryProductVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 购买流程的库存服务客户端。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@FeignClient(name = "inventory-service", fallbackFactory = InventoryServiceFallbackFactory.class)
public interface InventoryServiceFeign {

    /** 查询服务端商品资料。 */
    @GetMapping("/inventory/{productId}")
    Result<InventoryProductVO> getProductById(@PathVariable("productId") Long productId);

    /** 扣减全局事务内的库存。 */
    @PostMapping("/inventory/deduct")
    Result<Void> deduct(@RequestBody InventoryDeductRequest request);

    /** 返还全局事务内的库存。 */
    @PostMapping("/inventory/restore")
    Result<Void> restore(@RequestBody InventoryRestoreRequest request);
}
