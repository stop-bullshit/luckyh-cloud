package com.luckyh.cloud.common.web.context;

import lombok.Data;

/**
 * 用户上下文工具类
 * 
 * @author Lucky
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> USER_INFO = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    /**
     * 设置用户信息
     */
    public static void setUserInfo(UserInfo userInfo) {
        USER_INFO.set(userInfo);
        if (userInfo != null) {
            setUserId(userInfo.getUserId());
            setUsername(userInfo.getUsername());
        }
    }

    /**
     * 获取用户信息
     */
    public static UserInfo getUserInfo() {
        return USER_INFO.get();
    }

    /**
     * 设置用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取用户ID
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置用户名
     */
    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    /**
     * 获取用户名
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 清除用户上下文
     */
    public static void clear() {
        USER_INFO.remove();
        USER_ID.remove();
        USERNAME.remove();
    }

    /**
     * 用户信息内部类
     */
    @Data
    public static class UserInfo {
        private Long userId;
        private String username;
        private String realName;
        private Integer userType;
    }
}