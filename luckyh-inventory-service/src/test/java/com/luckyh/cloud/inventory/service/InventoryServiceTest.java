package com.luckyh.cloud.inventory.service;

import com.luckyh.cloud.inventory.dto.SaveProductRequest;
import com.luckyh.cloud.inventory.entity.Inventory;
import com.luckyh.cloud.inventory.mapper.InventoryMapper;
import io.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 商品新增、编辑与库存扣减的核心业务测试。
 *
 * @author Lucky
 * @since 2026-10-02
 */
class InventoryServiceTest {

    /** 不访问真实数据库的库存 Mapper。 */
    private InventoryMapper inventoryMapper;

    /** 被测库存业务服务。 */
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        RootContext.unbind();
        inventoryMapper = mock(InventoryMapper.class);
        inventoryService = new InventoryService(inventoryMapper);
    }

    @AfterEach
    void clearTransactionContext() {
        RootContext.unbind();
    }

    @Test
    void createProductUsesGeneratedIdAndZeroStock() {
        SaveProductRequest request = new SaveProductRequest();
        request.setProductName("测试商品");
        request.setProductPrice(new BigDecimal("12.34"));
        when(inventoryMapper.insert(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory inventory = invocation.getArgument(0);
            assertNull(inventory.getProductId());
            assertEquals(0, inventory.getAvailableQuantity());
            assertEquals("测试商品", inventory.getProductName());
            assertEquals(new BigDecimal("12.34"), inventory.getProductPrice());
            assertNull(inventory.getUpdateTime());
            inventory.setProductId(18L);
            return 1;
        });

        assertEquals(18L, inventoryService.createProduct(request));
    }

    @Test
    void createProductDoesNotReportSuccessWhenInsertFails() {
        SaveProductRequest request = new SaveProductRequest();
        request.setProductName("测试商品");
        request.setProductPrice(new BigDecimal("12.34"));
        when(inventoryMapper.insert(any(Inventory.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> inventoryService.createProduct(request));
    }

    @Test
    void updateProductUsesOnlyProductFieldsWithoutLoadingStock() {
        SaveProductRequest request = new SaveProductRequest();
        request.setProductName("修改商品");
        request.setProductPrice(new BigDecimal("20.00"));
        when(inventoryMapper.updateProduct(18L, "修改商品", new BigDecimal("20.00"))).thenReturn(1);

        assertTrue(inventoryService.updateProduct(18L, request));

        verify(inventoryMapper).updateProduct(18L, "修改商品", new BigDecimal("20.00"));
        verifyNoMoreInteractions(inventoryMapper);
    }

    @Test
    void replenishReturnsConditionalUpdateFailure() {
        when(inventoryMapper.replenish(18L, 10)).thenReturn(0);

        assertFalse(inventoryService.replenish(18L, 10));

        verify(inventoryMapper).replenish(18L, 10);
        verifyNoMoreInteractions(inventoryMapper);
    }

    @Test
    void deductSucceedsInGlobalTransaction() {
        RootContext.bind("inventory-test-success");
        when(inventoryMapper.deduct(1L, 2)).thenReturn(1);

        assertTrue(inventoryService.deduct(1L, 2));

        verify(inventoryMapper).deduct(1L, 2);
        verifyNoMoreInteractions(inventoryMapper);
    }

    @Test
    void deductReturnsFalseWhenStockIsInsufficient() {
        RootContext.bind("inventory-test-insufficient");
        when(inventoryMapper.deduct(1L, 2)).thenReturn(0);

        assertFalse(inventoryService.deduct(1L, 2));

        verify(inventoryMapper).deduct(1L, 2);
        verifyNoMoreInteractions(inventoryMapper);
    }

    @Test
    void deductRejectsMissingGlobalTransactionBeforeUpdatingStock() {
        assertThrows(IllegalStateException.class, () -> inventoryService.deduct(1L, 2));

        verifyNoInteractions(inventoryMapper);
    }
}
