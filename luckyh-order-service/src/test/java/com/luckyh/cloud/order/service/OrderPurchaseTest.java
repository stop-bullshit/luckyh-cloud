package com.luckyh.cloud.order.service;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.common.core.exception.BusinessException;
import com.luckyh.cloud.common.core.exception.ServiceException;
import com.luckyh.cloud.order.dto.PurchaseDTO;
import com.luckyh.cloud.order.entity.OrderInfo;
import com.luckyh.cloud.order.exception.PurchaseRollbackException;
import com.luckyh.cloud.order.feign.AccountServiceFeign;
import com.luckyh.cloud.order.feign.InventoryServiceFeign;
import com.luckyh.cloud.order.feign.UserServiceFeign;
import com.luckyh.cloud.order.mapper.OrderOperationLogMapper;
import com.luckyh.cloud.order.service.impl.OrderServiceImpl;
import com.luckyh.cloud.order.vo.InventoryProductVO;
import com.luckyh.cloud.order.vo.OrderVO;
import io.seata.core.context.RootContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderPurchaseTest {

    private final UserServiceFeign users = mock(UserServiceFeign.class);
    private final InventoryServiceFeign inventory = mock(InventoryServiceFeign.class);
    private final AccountServiceFeign accounts = mock(AccountServiceFeign.class);
    private final OrderOperationLogMapper operationLogs = mock(OrderOperationLogMapper.class);
    private OrderServiceImpl service;
    private PurchaseDTO request;

    @BeforeEach
    void setUp() {
        RootContext.bind("purchase-test-xid");
        service = spy(new OrderServiceImpl(users, inventory, accounts, operationLogs));
        request = new PurchaseDTO();
        request.setUserId(1L);
        request.setProductId(1L);
        request.setQuantity(2);
        OrderVO.UserInfo user = new OrderVO.UserInfo();
        user.setId(1L);
        InventoryProductVO product = new InventoryProductVO();
        product.setProductId(1L);
        product.setProductName("演示商品");
        product.setProductPrice(new BigDecimal("99.90"));
        when(users.getUserById(1L)).thenReturn(Result.success(user));
        when(inventory.getProductById(1L)).thenReturn(Result.success(product));
        when(inventory.deduct(any())).thenReturn(Result.success());
        when(accounts.debit(any())).thenReturn(Result.success());
        when(operationLogs.insert(any(com.luckyh.cloud.order.entity.OrderOperationLog.class))).thenReturn(1);
        doAnswer(call -> {
            ((OrderInfo) call.getArgument(0)).setId(42L);
            return true;
        }).when(service).save(any(OrderInfo.class));
    }

    @AfterEach
    void clearXid() {
        RootContext.unbind();
    }

    @Test
    void purchaseWritesOrderThenInventoryThenAccountWithServerPrice() {
        assertEquals(42L, service.purchase(request));
        InOrder sequence = inOrder(service, inventory, accounts);
        ArgumentCaptor<OrderInfo> order = ArgumentCaptor.forClass(OrderInfo.class);
        sequence.verify(inventory).getProductById(1L);
        sequence.verify(service).save(order.capture());
        sequence.verify(inventory).deduct(argThat(value -> value.getQuantity() == 2));
        sequence.verify(accounts).debit(argThat(value -> new BigDecimal("199.80").compareTo(value.getAmount()) == 0));
        assertEquals(1, order.getValue().getStatus());
        assertEquals(new BigDecimal("199.80"), order.getValue().getTotalAmount());
    }

    @Test
    void inventoryFailureThrowsBeforeAccountCall() {
        when(inventory.deduct(any())).thenReturn(Result.error(409, "库存不足"));
        BusinessException error = assertThrows(BusinessException.class, () -> service.purchase(request));
        assertEquals(409, error.getCode());
        assertEquals("库存扣减失败：库存不足", error.getMessage());
        verify(accounts, never()).debit(any());
    }

    @Test
    void accountFailureEscapesTheTransactionBoundary() {
        when(accounts.debit(any())).thenReturn(Result.error(409, "余额不足"));
        BusinessException error = assertThrows(BusinessException.class, () -> service.purchase(request));
        assertEquals(409, error.getCode());
        assertEquals("账户扣款失败：余额不足", error.getMessage());
        verify(service).save(any(OrderInfo.class));
        verify(inventory).deduct(any());
    }

    @Test
    void downstreamTimeoutKeeps504AndRollsBack() {
        when(accounts.debit(any())).thenReturn(Result.error(504, "internal timeout detail"));
        ServiceException error = assertThrows(ServiceException.class, () -> service.purchase(request));
        assertEquals(504, error.getCode());
        assertEquals("账户扣款失败：服务请求超时", error.getMessage());
        verify(service).save(any(OrderInfo.class));
    }

    @Test
    void downstreamInternalFailureKeeps500AndHidesTechnicalMessage() {
        when(inventory.deduct(any())).thenReturn(Result.error(500, "database connection string"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.purchase(request));
        assertEquals("库存扣减失败：下游服务处理失败", error.getMessage());
        verify(accounts, never()).debit(any());
    }

    @Test
    void unavailableDownstreamKeeps503() {
        when(inventory.deduct(any())).thenReturn(Result.error(503, "internal address"));
        ServiceException error = assertThrows(ServiceException.class, () -> service.purchase(request));
        assertEquals(503, error.getCode());
        assertEquals("库存扣减失败：服务暂不可用", error.getMessage());
        verify(accounts, never()).debit(any());
    }

    @Test
    void historicalOrderWithoutProductIdHasActionableRefundError() {
        OrderInfo order = new OrderInfo();
        order.setStatus(1);
        doReturn(order).when(service).getById(42L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.refundOrder(42L));
        assertEquals(409, error.getCode());
        assertEquals("订单缺少商品ID，无法返还库存", error.getMessage());
        verifyNoInteractions(accounts, inventory);
    }

    @Test
    void historicalOrderWithoutProductIdHasActionablePayError() {
        OrderInfo order = new OrderInfo();
        order.setStatus(0);
        doReturn(order).when(service).getById(42L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.payOrder(42L));
        assertEquals(409, error.getCode());
        assertEquals("历史订单缺少商品ID，无法扣减库存和余额", error.getMessage());
        verifyNoInteractions(accounts, inventory);
    }

    @Test
    void forcedRollbackOnlyOccursAfterAllWrites() {
        assertThrows(PurchaseRollbackException.class, () -> service.purchaseWithRollback(request));
        verify(service).save(any(OrderInfo.class));
        verify(inventory).deduct(any());
        verify(accounts).debit(any());
    }

    @Test
    void missingGlobalTransactionCannotWriteAnything() {
        RootContext.unbind();
        assertThrows(IllegalStateException.class, () -> service.purchase(request));
        verifyNoInteractions(users, inventory, accounts);
        verify(service, never()).save(any(OrderInfo.class));
    }
}
