package com.luckyh.cloud.order.feign;

import com.luckyh.cloud.order.common.Result;
import com.luckyh.cloud.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户服务降级处理
 */
@Slf4j
@Component
public class UserServiceFallback implements UserServiceFeign {

    @Override
    public Result<OrderVO.UserInfo> getUserById(Long userId) {
        log.warn("调用用户服务失败，启用降级处理，用户ID：{}", userId);

        OrderVO.UserInfo userInfo = new OrderVO.UserInfo();
        userInfo.setId(userId);
        userInfo.setUsername("未知用户");
        userInfo.setRealName("未知用户");

        return Result.success(userInfo);
    }
}
