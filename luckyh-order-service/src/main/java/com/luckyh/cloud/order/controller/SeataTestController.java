package com.luckyh.cloud.order.controller;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.order.dto.PurchaseDTO;
import com.luckyh.cloud.order.exception.PurchaseRollbackException;
import com.luckyh.cloud.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * Seata分布式事务演示控制器
 * 用于演示和测试Seata分布式事务功能
 */
@Slf4j
@RestController
@RequestMapping("/seata-demo")
@RequiredArgsConstructor
@Tag(name = "Seata演示", description = "Seata分布式事务演示接口")
public class SeataTestController {

    private final OrderService orderService;

    /**
     * 测试正常提交场景
     * 创建订单，所有操作都成功，事务提交
     */
    @PostMapping("/test-commit")
    @Operation(summary = "测试事务提交", description = "创建订单，所有操作成功，事务正常提交")
    public Result<Long> testCommit(@Valid @RequestBody PurchaseDTO purchaseDTO) {
        return Result.success("订单、库存、账户事务提交成功", orderService.purchase(purchaseDTO));
    }

    /**
     * 测试回滚场景
     * 完成订单、库存和账户写入后主动失败，验证三个数据库回滚
     */
    @PostMapping("/test-rollback")
    @Operation(summary = "测试事务回滚", description = "三个服务写入后主动抛错，验证全局回滚")
    public Result<String> testRollback(@Valid @RequestBody PurchaseDTO purchaseDTO) {
        try {
            orderService.purchaseWithRollback(purchaseDTO);
            return Result.error(500, "预期应该失败，但却成功了");
        } catch (RuntimeException e) {
            Throwable cause = e;
            // Seata 2.0 的调用适配器会包装业务异常，只识别它直接包装的演示异常。
            if (e.getClass() == RuntimeException.class && "try to proceed invocation error".equals(e.getMessage())) {
                cause = e.getCause();
            }
            if (!(cause instanceof PurchaseRollbackException expected)) {
                throw e;
            }
            log.info("已触发三个购买分支回滚：{}", expected.getMessage());
            return Result.success("已触发全局回滚，请核对订单、库存、余额与协调器状态", expected.getMessage());
        }
    }

    /**
     * 获取Seata集成说明
     */
    @GetMapping("/info")
    @Operation(summary = "获取Seata信息", description = "获取Seata分布式事务集成说明")
    public Result<SeataInfo> getSeataInfo() {
        SeataInfo info = new SeataInfo();
        info.setEnabled(true);
        info.setMode("AT");
        info.setVersion("2.0.0");
        info.setDescription("购买流程在订单、库存、账户三个独立数据库执行AT分支，信息页不代替运行验证");
        info.setTransactionMethods(new String[]{
            "OrderService.purchase() - 订单、库存、账户提交",
            "OrderService.purchaseWithRollback() - 三个写入分支完成后回滚",
            "OrderService.createOrder() - 创建订单事务",
            "OrderService.payOrder() - 支付订单事务",
            "OrderService.cancelOrder() - 取消订单事务"
        });
        return Result.success(info);
    }

    /**
     * Seata信息VO
     */
    public static class SeataInfo {
        private Boolean enabled;
        private String mode;
        private String version;
        private String description;
        private String[] transactionMethods;

        // Getters and Setters
        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String[] getTransactionMethods() {
            return transactionMethods;
        }

        public void setTransactionMethods(String[] transactionMethods) {
            this.transactionMethods = transactionMethods;
        }
    }
}
