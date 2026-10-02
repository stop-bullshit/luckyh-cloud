package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.order.dto.AccountDebitRequest;
import com.luckyh.cloud.order.dto.AccountCreditRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 购买流程的账户服务客户端。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@FeignClient(name = "account-service", fallbackFactory = AccountServiceFallbackFactory.class)
public interface AccountServiceFeign {

    /** 扣减全局事务内的用户余额。 */
    @PostMapping("/accounts/debit")
    Result<Void> debit(@RequestBody AccountDebitRequest request);

    /** 退回全局事务内的用户余额。 */
    @PostMapping("/accounts/credit")
    Result<Void> credit(@RequestBody AccountCreditRequest request);
}
