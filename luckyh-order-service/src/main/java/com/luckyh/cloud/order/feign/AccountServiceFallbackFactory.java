package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 账户服务降级，记录扣款参数和触发异常。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Slf4j
@Component
public class AccountServiceFallbackFactory implements FallbackFactory<AccountServiceFeign> {

    @Override
    public AccountServiceFeign create(Throwable cause) {
        return new AccountServiceFeign() {
            @Override
            public Result<Void> debit(com.luckyh.cloud.order.dto.AccountDebitRequest request) {
                log.error("Feign降级 service=account-service method=debit userId={} amount={} xid={}",
                        request.getUserId(), request.getAmount(), RootContext.getXID(), cause);
                return FeignFallbackErrors.response(cause, "账户服务");
            }

            @Override
            public Result<Void> credit(com.luckyh.cloud.order.dto.AccountCreditRequest request) {
                log.error("Feign降级 service=account-service method=credit userId={} amount={} xid={}",
                        request.getUserId(), request.getAmount(), RootContext.getXID(), cause);
                return FeignFallbackErrors.response(cause, "账户服务");
            }
        };
    }
}
