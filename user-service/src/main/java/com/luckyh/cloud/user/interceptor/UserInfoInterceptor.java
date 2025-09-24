package com.luckyh.cloud.user.interceptor;

import cn.hutool.core.util.StrUtil;
import com.luckyh.cloud.common.web.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 用户信息拦截器
 */
@Slf4j
@Component
public class UserInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头中获取用户信息
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-Username");
        String realName = request.getHeader("X-Real-Name");
        String userType = request.getHeader("X-User-Type");

        if (StrUtil.isNotBlank(userId)) {
            try {
                UserContext.UserInfo userInfo = new UserContext.UserInfo();
                userInfo.setUserId(Long.parseLong(userId));
                userInfo.setUsername(username);
                userInfo.setRealName(realName);
                if (StrUtil.isNotBlank(userType)) {
                    userInfo.setUserType(Integer.parseInt(userType));
                }

                UserContext.setUserInfo(userInfo);
                log.debug("设置用户上下文: userId={}, username={}", userId, username);
            } catch (NumberFormatException e) {
                log.warn("解析用户信息失败: {}", e.getMessage());
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        // 清除用户上下文
        UserContext.clear();
    }
}
