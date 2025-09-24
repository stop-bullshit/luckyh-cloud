package com.luckyh.cloud.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luckyh.cloud.order.common.Result;
import com.luckyh.cloud.order.dto.OrderDTO;
import com.luckyh.cloud.order.service.OrderService;
import com.luckyh.cloud.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单
     */
    @PostMapping
    public Result<Long> createOrder(@RequestBody @Validated OrderDTO orderDTO) {
        Long orderId = orderService.createOrder(orderDTO);
        if (orderId != null) {
            return Result.success("订单创建成功", orderId);
        }
        return Result.error("订单创建失败");
    }

    /**
     * 根据ID获取订单
     */
    @GetMapping("/{id}")
    public Result<OrderVO> getOrderById(@PathVariable Long id) {
        OrderVO orderVO = orderService.getOrderById(id);
        if (orderVO != null) {
            return Result.success(orderVO);
        }
        return Result.error("订单不存在");
    }

    /**
     * 分页查询订单
     */
    @GetMapping
    public Result<Page<OrderVO>> getOrderPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Long userId) {
        Page<OrderVO> orderPage = orderService.getOrderPage(current, size, userId);
        return Result.success(orderPage);
    }

    /**
     * 支付订单
     */
    @PostMapping("/{id}/pay")
    public Result<String> payOrder(@PathVariable Long id) {
        boolean paid = orderService.payOrder(id);
        if (paid) {
            return Result.success("订单支付成功");
        }
        return Result.error("订单支付失败");
    }

    /**
     * 取消订单
     */
    @PostMapping("/{id}/cancel")
    public Result<String> cancelOrder(@PathVariable Long id) {
        boolean cancelled = orderService.cancelOrder(id);
        if (cancelled) {
            return Result.success("订单取消成功");
        }
        return Result.error("订单取消失败");
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("订单服务正常运行");
    }
}
