package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务降级，保留原始异常且返回失败结果。
 *
 * @author Lucky
 * @since 2026-10-02
 */
@Slf4j
@Component
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceFeign> {

    @Override
    public UserServiceFeign create(Throwable cause) {
        return new UserServiceFeign() {
            @Override
            public Result<com.luckyh.cloud.order.vo.OrderVO.UserInfo> getUserById(Long userId) {
                log.error("Feign降级 service=auth-service method=getUserById userId={} xid={}",
                        userId, RootContext.getXID(), cause);
                return Result.error(503, "认证用户服务暂不可用");
            }

            @Override
            public Result<java.util.List<com.luckyh.cloud.order.vo.OrderVO.UserInfo>> getUsersByIds(
                    java.util.List<Long> ids) {
                log.error("Feign降级 service=auth-service method=getUsersByIds userIds={} xid={}",
                        ids, RootContext.getXID(), cause);
                return Result.error(503, "认证用户服务暂不可用");
            }
        };
    }
}
