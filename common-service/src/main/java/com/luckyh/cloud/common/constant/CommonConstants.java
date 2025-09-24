package com.luckyh.cloud.common.constant;

/**
 * 通用常量
 */
public interface CommonConstants {
    
    /**
     * 成功标识
     */
    String SUCCESS = "SUCCESS";
    
    /**
     * 失败标识
     */
    String FAIL = "FAIL";
    
    /**
     * 默认分页大小
     */
    int DEFAULT_PAGE_SIZE = 10;
    
    /**
     * 最大分页大小
     */
    int MAX_PAGE_SIZE = 100;
    
    /**
     * 默认排序字段
     */
    String DEFAULT_SORT_FIELD = "id";
    
    /**
     * 升序
     */
    String ASC = "asc";
    
    /**
     * 降序
     */
    String DESC = "desc";
    
    /**
     * UTF-8编码
     */
    String UTF8 = "UTF-8";
    
    /**
     * JSON内容类型
     */
    String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";
    
    /**
     * 是
     */
    Integer YES = 1;
    
    /**
     * 否
     */
    Integer NO = 0;
    
    /**
     * 启用状态
     */
    Integer ENABLED = 1;
    
    /**
     * 禁用状态
     */
    Integer DISABLED = 0;
    
    /**
     * 删除标识
     */
    Integer DELETED = 1;
    
    /**
     * 未删除标识
     */
    Integer NOT_DELETED = 0;
    
    /**
     * 超级管理员角色
     */
    String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    
    /**
     * 管理员角色
     */
    String ROLE_ADMIN = "ADMIN";
    
    /**
     * 普通用户角色
     */
    String ROLE_USER = "USER";
    
    /**
     * 默认密码
     */
    String DEFAULT_PASSWORD = "123456";
}
