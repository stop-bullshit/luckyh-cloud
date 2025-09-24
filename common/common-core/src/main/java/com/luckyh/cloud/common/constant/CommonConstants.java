package com.luckyh.cloud.common.constant;

/**
 * 通用常量类
 * 
 * @author LuckyH
 */
public class CommonConstants {

    /**
     * 成功标识
     */
    public static final String SUCCESS = "SUCCESS";

    /**
     * 失败标识
     */
    public static final String FAIL = "FAIL";

    /**
     * 成功代码
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * 失败代码
     */
    public static final int FAIL_CODE = 500;

    /**
     * 未授权代码
     */
    public static final int UNAUTHORIZED_CODE = 401;

    /**
     * 禁止访问代码
     */
    public static final int FORBIDDEN_CODE = 403;

    /**
     * 资源未找到代码
     */
    public static final int NOT_FOUND_CODE = 404;

    /**
     * UTF-8编码
     */
    public static final String UTF8 = "UTF-8";

    /**
     * GBK编码
     */
    public static final String GBK = "GBK";

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 默认页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大页大小
     */
    public static final int MAX_PAGE_SIZE = 1000;

    /**
     * 是否删除标识 - 未删除
     */
    public static final Integer NOT_DELETED = 0;

    /**
     * 是否删除标识 - 已删除
     */
    public static final Integer DELETED = 1;

    /**
     * 用户状态 - 正常
     */
    public static final Integer USER_STATUS_NORMAL = 1;

    /**
     * 用户状态 - 禁用
     */
    public static final Integer USER_STATUS_DISABLED = 0;

    /**
     * 性别 - 男
     */
    public static final Integer GENDER_MALE = 1;

    /**
     * 性别 - 女
     */
    public static final Integer GENDER_FEMALE = 2;

    /**
     * 性别 - 未知
     */
    public static final Integer GENDER_UNKNOWN = 0;

}