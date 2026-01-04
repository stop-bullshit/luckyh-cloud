package com.luckyh.cloud.order.controller;

import com.luckyh.cloud.order.common.Result;
import com.luckyh.cloud.order.dto.OrderDTO;
import com.luckyh.cloud.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
    public Result<Long> testCommit(@RequestBody OrderDTO orderDTO) {
        log.info("===== Seata分布式事务测试：正常提交场景 =====");
        try {
            Long orderId = orderService.createOrder(orderDTO);
            log.info("订单创建成功，订单ID: {}", orderId);
            return Result.success("事务提交成功", orderId);
        } catch (Exception e) {
            log.error("订单创建失败", e);
            return Result.error(500, "事务执行失败: " + e.getMessage());
        }
    }

    /**
     * 测试回滚场景
     * 使用不存在的用户ID创建订单，验证事务回滚
     */
    @PostMapping("/test-rollback")
    @Operation(summary = "测试事务回滚", description = "使用不存在的用户ID，验证事务回滚")
    public Result<String> testRollback() {
        log.info("===== Seata分布式事务测试：回滚场景 =====");
        
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setUserId(999999L); // 使用不存在的用户ID
        orderDTO.setProductName("测试商品-回滚场景");
        orderDTO.setProductPrice(new java.math.BigDecimal("99.99"));
        orderDTO.setQuantity(1);
        
        try {
            orderService.createOrder(orderDTO);
            return Result.error(500, "预期应该失败，但却成功了");
        } catch (Exception e) {
            log.info("事务已回滚，异常信息: {}", e.getMessage());
            return Result.success("事务回滚测试成功，异常信息: " + e.getMessage(), null);
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
        info.setDescription("已集成Seata分布式事务框架，支持AT模式自动补偿");
        info.setTransactionMethods(new String[]{
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
