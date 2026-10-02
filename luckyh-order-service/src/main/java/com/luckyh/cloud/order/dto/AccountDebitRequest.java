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
}
