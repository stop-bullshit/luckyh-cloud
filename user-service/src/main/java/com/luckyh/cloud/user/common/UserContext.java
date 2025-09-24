package com.luckyh.cloud.user.common;

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
     * 清除用户信息
     */
    public static void clear() {
        USER_HOLDER.remove();
    }
    
    /**
     * 用户信息内部类
     */
    public static class UserInfo {
        private Long userId;
        private String username;
        private String realName;
        private Integer userType;
        
        public UserInfo() {}
        
        public UserInfo(Long userId, String username, String realName, Integer userType) {
            this.userId = userId;
            this.username = username;
            this.realName = realName;
            this.userType = userType;
        }
        
        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        
        public Integer getUserType() { return userType; }
        public void setUserType(Integer userType) { this.userType = userType; }
    }
}
