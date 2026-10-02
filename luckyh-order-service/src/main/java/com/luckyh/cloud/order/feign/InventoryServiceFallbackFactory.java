package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.order.dto.InventoryDeductRequest;
import com.luckyh.cloud.order.vo.InventoryProductVO;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存服务降级，记录商品参数和触发异常。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Slf4j
@Component
public class InventoryServiceFallbackFactory implements FallbackFactory<InventoryServiceFeign> {

    @Override
    public InventoryServiceFeign create(Throwable cause) {
        return new InventoryServiceFeign() {
            @Override
            public Result<InventoryProductVO> getProductById(Long productId) {
                log.error("Feign降级 service=inventory-service method=getProductById productId={} xid={}",
                        productId, RootContext.getXID(), cause);
                return Result.error(503, "库存服务暂不可用");
            }

            @Override
            public Result<Void> deduct(InventoryDeductRequest request) {
                log.error("Feign降级 service=inventory-service method=deduct productId={} quantity={} xid={}",
                        request.getProductId(), request.getQuantity(), RootContext.getXID(), cause);
                return Result.error(503, "库存服务暂不可用");
            }

            @Override
            public Result<Void> restore(com.luckyh.cloud.order.dto.InventoryRestoreRequest request) {
                log.error("Feign降级 service=inventory-service method=restore productId={} quantity={} xid={}",
                        request.getProductId(), request.getQuantity(), RootContext.getXID(), cause);
                return Result.error(503, "库存服务暂不可用");
            }
        };
    }
}
