package com.luckyh.cloud.common.context;

import lombok.Data;

/**
 * 用户上下文
 */
public class UserContext {
    
    private static final ThreadLocal<UserInfo> USER_HOLDER = new ThreadLocal<>();
    
    /**
     * 设置用户信息
     */
    public static void setUser(UserInfo userInfo) {
        USER_HOLDER.set(userInfo);
    }
    
    /**
     * 获取用户信息
     */
    public static UserInfo getUser() {
        return USER_HOLDER.get();
    }
    
    /**
     * 获取用户ID
     */
    public static Long getUserId() {
        UserInfo userInfo = getUser();
        return userInfo != null ? userInfo.getUserId() : null;
    }
    
    /**
     * 获取用户名
     */
    public static String getUsername() {
        UserInfo userInfo = getUser();
        return userInfo != null ? userInfo.getUsername() : null;
    }
    
    /**
     * 获取真实姓名
     */
    public static String getRealName() {
        UserInfo userInfo = getUser();
        return userInfo != null ? userInfo.getRealName() : null;
    }
    
    /**
     * 获取用户类型
     */
    public static Integer getUserType() {
        UserInfo userInfo = getUser();
        return userInfo != null ? userInfo.getUserType() : null;
    }
    
    /**
     * 清除用户信息
     */
    public static void clear() {
        USER_HOLDER.remove();
    }
    
    /**
     * 用户信息
     */
    @Data
    public static class UserInfo {
        private Long userId;
        private String username;
        private String realName;
        private String email;
        private String phone;
        private Integer userType;
        
        public UserInfo() {}
        
        public UserInfo(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }
        
        public UserInfo(Long userId, String username, String realName, Integer userType) {
            this.userId = userId;
            this.username = username;
            this.realName = realName;
            this.userType = userType;
        }
    }
}
