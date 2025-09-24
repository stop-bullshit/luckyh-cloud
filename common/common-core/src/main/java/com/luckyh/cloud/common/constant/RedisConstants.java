package com.luckyh.cloud.common.constant;

/**
 * Redis常量类
 * 
 * @author LuckyH
 */
public class RedisConstants {

    /**
     * 分隔符
     */
    public static final String SEPARATOR = ":";

    /**
     * Token黑名单前缀
     */
    public static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";

    /**
     * 用户缓存前缀
     */
    public static final String USER_CACHE_PREFIX = "user:";

    /**
     * 验证码前缀
     */
    public static final String CAPTCHA_PREFIX = "captcha:";

    /**
     * 短信验证码前缀
     */
    public static final String SMS_CODE_PREFIX = "sms:code:";

    /**
     * 邮箱验证码前缀
     */
    public static final String EMAIL_CODE_PREFIX = "email:code:";

    /**
     * 登录失败次数前缀
     */
    public static final String LOGIN_FAIL_PREFIX = "login:fail:";

    /**
     * 分布式锁前缀
     */
    public static final String LOCK_PREFIX = "lock:";

    /**
     * 限流前缀
     */
    public static final String RATE_LIMIT_PREFIX = "rate:limit:";

    /**
     * 缓存空值，防止缓存穿透
     */
    public static final String CACHE_NULL_VALUE = "NULL";

    /**
     * 默认缓存时间（秒）
     */
    public static final long DEFAULT_EXPIRE_TIME = 3600L;

    /**
     * 短时间缓存（秒）
     */
    public static final long SHORT_EXPIRE_TIME = 300L;

    /**
     * 长时间缓存（秒）
     */
    public static final long LONG_EXPIRE_TIME = 86400L;

}