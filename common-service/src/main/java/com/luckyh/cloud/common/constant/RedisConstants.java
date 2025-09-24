package com.luckyh.cloud.common.constant;

/**
 * Redis Key常量
 */
public interface RedisConstants {
    
    /**
     * Redis key前缀
     */
    String KEY_PREFIX = "luckyh:cloud:";
    
    /**
     * JWT token黑名单前缀
     */
    String JWT_BLACKLIST_PREFIX = KEY_PREFIX + "jwt:blacklist:";
    
    /**
     * 用户信息缓存前缀
     */
    String USER_INFO_PREFIX = KEY_PREFIX + "user:info:";
    
    /**
     * 用户权限缓存前缀
     */
    String USER_PERMISSION_PREFIX = KEY_PREFIX + "user:permission:";
    
    /**
     * 验证码前缀
     */
    String CAPTCHA_PREFIX = KEY_PREFIX + "captcha:";
    
    /**
     * 登录失败次数前缀
     */
    String LOGIN_FAIL_PREFIX = KEY_PREFIX + "login:fail:";
    
    /**
     * 分布式锁前缀
     */
    String LOCK_PREFIX = KEY_PREFIX + "lock:";
    
    /**
     * 限流前缀
     */
    String RATE_LIMIT_PREFIX = KEY_PREFIX + "rate:limit:";
    
    /**
     * 会话前缀
     */
    String SESSION_PREFIX = KEY_PREFIX + "session:";
    
    /**
     * 在线用户前缀
     */
    String ONLINE_USER_PREFIX = KEY_PREFIX + "online:user:";
    
    /**
     * 配置缓存前缀
     */
    String CONFIG_PREFIX = KEY_PREFIX + "config:";
    
    /**
     * 字典缓存前缀
     */
    String DICT_PREFIX = KEY_PREFIX + "dict:";
    
    /**
     * 菜单缓存前缀
     */
    String MENU_PREFIX = KEY_PREFIX + "menu:";
    
    /**
     * 默认过期时间（秒）
     */
    long DEFAULT_EXPIRE_TIME = 3600;
    
    /**
     * Token过期时间（秒）
     */
    long TOKEN_EXPIRE_TIME = 86400;
    
    /**
     * 验证码过期时间（秒）
     */
    long CAPTCHA_EXPIRE_TIME = 300;
    
    /**
     * 用户信息缓存过期时间（秒）
     */
    long USER_INFO_EXPIRE_TIME = 1800;
    
    /**
     * 登录失败锁定时间（秒）
     */
    long LOGIN_FAIL_LOCK_TIME = 600;
}
