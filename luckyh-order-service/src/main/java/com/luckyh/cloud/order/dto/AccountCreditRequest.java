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
}
