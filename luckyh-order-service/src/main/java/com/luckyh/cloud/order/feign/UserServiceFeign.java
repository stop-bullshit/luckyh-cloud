package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.order.vo.OrderVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

/**
 * 认证用户服务 Feign 客户端。
 */
@FeignClient(value = "auth-service", fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceFeign {

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/auth/users/{userId}")
    Result<OrderVO.UserInfo> getUserById(@PathVariable("userId") Long userId);

    /** 批量查询登录用户。 */
    @GetMapping("/auth/users/batch")
    Result<List<OrderVO.UserInfo>> getUsersByIds(@RequestParam("ids") List<Long> ids);
}
