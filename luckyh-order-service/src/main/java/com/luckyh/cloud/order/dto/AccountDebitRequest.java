package com.luckyh.cloud.order.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账户服务扣款参数。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDebitRequest {

    /** 登录用户 ID。 */
    private Long userId;

    /** 订单实际扣款金额。 */
    private BigDecimal amount;

    /** 可选订单号，供账户余额明细关联订单。 */
    private String orderNo;

    /** 保留不提供订单号的旧调用方式。 */
    public AccountDebitRequest(Long userId, BigDecimal amount) {
        this(userId, amount, null);
    }
}
