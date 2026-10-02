package com.luckyh.cloud.order.controller;

import com.luckyh.cloud.common.core.exception.BusinessException;
import com.luckyh.cloud.common.web.exception.GlobalExceptionHandler;
import com.luckyh.cloud.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerErrorTest {

    @Test
    void refundMissingProductIdShowsBusinessMessage() throws Exception {
        OrderService orderService = mock(OrderService.class);
        when(orderService.refundOrder(42L))
                .thenThrow(new BusinessException(409, "订单缺少商品ID，无法返还库存"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/orders/42/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单缺少商品ID，无法返还库存"));
    }

    @Test
    void refundInvalidOrderStateReturnsConflict() throws Exception {
        OrderService orderService = mock(OrderService.class);
        when(orderService.refundOrder(42L)).thenReturn(false);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/orders/42/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单不存在或当前状态不能退款"));
    }
}
