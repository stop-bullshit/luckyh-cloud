package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.order.common.Result;
import com.luckyh.cloud.order.vo.OrderVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务Feign客户端
 */
@FeignClient(value = "user-service", fallback = UserServiceFallback.class)
public interface UserServiceFeign {

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/users/{userId}")
    Result<OrderVO.UserInfo> getUserById(@PathVariable("userId") Long userId);
}
