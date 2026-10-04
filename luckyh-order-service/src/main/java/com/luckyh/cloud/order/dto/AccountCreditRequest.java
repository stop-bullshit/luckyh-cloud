package com.luckyh.cloud.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 账户退款分支请求.
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreditRequest {

    /** 收款用户 ID。 */
    private Long userId;

    /** 退款金额。 */
    private BigDecimal amount;

    /** 可选订单号，供账户余额明细关联退款订单。 */
    private String orderNo;

    /** 保留不提供订单号的旧调用方式。 */
    public AccountCreditRequest(Long userId, BigDecimal amount) {
        this(userId, amount, null);
    }
}
