package com.luckyh.cloud.order.exception;

/**
 * 购买分支全部完成后主动触发的演示回滚异常。
 *
 * @author Lucky
 * @since 2026-10-02
 */
public class PurchaseRollbackException extends RuntimeException {

    public PurchaseRollbackException(Long orderId, String xid) {
        super("演示回滚：订单、库存和账户分支已完成，订单ID=" + orderId + "，XID=" + xid);
    }
}
