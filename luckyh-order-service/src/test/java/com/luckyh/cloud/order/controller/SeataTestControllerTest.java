package com.luckyh.cloud.order.controller;

import com.luckyh.cloud.order.dto.PurchaseDTO;
import com.luckyh.cloud.order.exception.PurchaseRollbackException;
import com.luckyh.cloud.order.service.OrderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeataTestControllerTest {

    private final OrderService service = mock(OrderService.class);
    private final SeataTestController controller = new SeataTestController(service);
    private final PurchaseDTO request = new PurchaseDTO();

    @Test
    void directDemoExceptionReportsOnlyThatRollbackWasTriggered() {
        doThrow(new PurchaseRollbackException(42L, "demo-xid")).when(service).purchaseWithRollback(request);
        var result = controller.testRollback(request);
        assertEquals(200, result.getCode());
        assertTrue(result.getMessage().contains("请核对"));
        assertTrue(result.getData().contains("demo-xid"));
    }

    @Test
    void seataInvocationWrapperStillIdentifiesTheDemoException() {
        doThrow(new RuntimeException("try to proceed invocation error", new PurchaseRollbackException(42L, "demo-xid")))
                .when(service).purchaseWithRollback(request);
        assertEquals(200, controller.testRollback(request).getCode());
    }

    @Test
    void actualBusinessFailureEscapesUnchanged() {
        RuntimeException failure = new RuntimeException("try to proceed invocation error", new IllegalStateException("余额不足"));
        doThrow(failure).when(service).purchaseWithRollback(request);
        assertSame(failure, assertThrows(RuntimeException.class, () -> controller.testRollback(request)));
    }

    @Test
    void rollbackFailureContainingTheMarkerIsNotReportedAsTheDemoResult() {
        RuntimeException failure = new IllegalStateException("rollback failed", new PurchaseRollbackException(42L, "demo-xid"));
        doThrow(failure).when(service).purchaseWithRollback(request);
        assertSame(failure, assertThrows(RuntimeException.class, () -> controller.testRollback(request)));
    }
}
