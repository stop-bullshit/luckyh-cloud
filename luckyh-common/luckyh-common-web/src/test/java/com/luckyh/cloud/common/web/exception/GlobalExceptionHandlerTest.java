package com.luckyh.cloud.common.web.exception;

import com.luckyh.cloud.common.core.exception.BusinessException;
import com.luckyh.cloud.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一异常响应的 HTTP 状态与消息检查。
 *
 * @author Lucky
 * @since 2026-10-02
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesOnlyExplicitBusinessErrors() throws Exception {
        mockMvc.perform(get("/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单缺少商品ID，无法返还库存"));

        mockMvc.perform(get("/wrapped-business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单缺少商品ID，无法返还库存"));
    }

    @Test
    void returnsServiceStatuses() throws Exception {
        mockMvc.perform(get("/service-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("库存服务暂不可用"));

        mockMvc.perform(get("/service-timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value(504))
                .andExpect(jsonPath("$.message").value("库存服务响应超时"));
    }

    @Test
    void hidesUnknownExceptionDetails() throws Exception {
        String body = mockMvc.perform(get("/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务处理失败，请稍后重试"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("jdbc password"));
    }

    @Test
    void keepsClientRequestStatuses() throws Exception {
        mockMvc.perform(get("/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数错误"));

        mockMvc.perform(post("/required").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @RestController
    static class ErrorController {

        @GetMapping("/business")
        public void business() {
            throw new BusinessException(409, "订单缺少商品ID，无法返还库存");
        }

        @GetMapping("/wrapped-business")
        public void wrappedBusiness() {
            throw new IllegalStateException("事务执行失败", new BusinessException(409, "订单缺少商品ID，无法返还库存"));
        }

        @GetMapping("/service-unavailable")
        public void serviceUnavailable() {
            throw new ServiceException(503, "库存服务暂不可用");
        }

        @GetMapping("/service-timeout")
        public void serviceTimeout() {
            throw new IllegalStateException("调用失败", new ServiceException(504, "库存服务响应超时"));
        }

        @GetMapping("/unknown")
        public void unknown() {
            throw new IllegalStateException("jdbc password=secret");
        }

        @GetMapping("/required")
        public void required(@RequestParam String id) {
        }
    }
}
