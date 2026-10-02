package com.luckyh.cloud.inventory.controller;

import com.luckyh.cloud.inventory.dto.SaveProductRequest;
import com.luckyh.cloud.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品接口的参数边界与失败响应测试，不访问数据库。
 *
 * @author Lucky
 * @since 2026-10-02
 */
class InventoryControllerTest {

    /** 不访问数据库的库存服务。 */
    private InventoryService inventoryService;

    /** 通过 HTTP 参数绑定验证请求边界。 */
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryController(inventoryService)).build();
    }

    @Test
    void createAcceptsPriceBoundariesAndReturnsGeneratedId() throws Exception {
        when(inventoryService.createProduct(any(SaveProductRequest.class))).thenReturn(18L);

        for (String price : List.of("0.01", "99999999.99")) {
            mockMvc.perform(post("/inventory").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productName\":\"测试商品\",\"productPrice\":" + price + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(18));
        }
    }

    @Test
    void createRejectsInvalidNamesAndPricesBeforeCallingService() throws Exception {
        for (String body : List.of(
                "{\"productName\":\" \",\"productPrice\":1}",
                "{\"productName\":\"" + "长".repeat(129) + "\",\"productPrice\":1}",
                "{\"productName\":\"测试商品\",\"productPrice\":0}",
                "{\"productName\":\"测试商品\",\"productPrice\":1.001}",
                "{\"productName\":\"测试商品\",\"productPrice\":100000000}",
                "{\"productName\":\"测试商品\"}")) {
            mockMvc.perform(post("/inventory").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(inventoryService);
    }

    @Test
    void replenishRejectsFractionalAndOutOfRangeQuantities() throws Exception {
        for (String quantity : List.of("null", "0", "-1", "1.5", "2147483648")) {
            mockMvc.perform(post("/inventory/18/replenish").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":" + quantity + "}"))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(inventoryService);
    }

    @Test
    void writeFailuresReturnBusinessErrorsAndValidReplenishmentSucceeds() throws Exception {
        when(inventoryService.updateProduct(eq(18L), any(SaveProductRequest.class))).thenReturn(false);
        when(inventoryService.replenish(18L, 1)).thenReturn(false);
        when(inventoryService.replenish(18L, Integer.MAX_VALUE)).thenReturn(true);

        mockMvc.perform(put("/inventory/18").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"测试商品\",\"productPrice\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(post("/inventory/18/replenish").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
        mockMvc.perform(post("/inventory/18/replenish").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2147483647}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(inventoryService).replenish(18L, Integer.MAX_VALUE);
    }

    @Test
    void listRejectsInvalidPaginationBeforeCallingService() throws Exception {
        for (String query : List.of("current=0", "size=0", "size=101")) {
            mockMvc.perform(get("/inventory?" + query)).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(inventoryService);
    }
}
