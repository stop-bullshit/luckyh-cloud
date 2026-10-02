package com.luckyh.cloud.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台登录用户视图。
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
public class ManagedUserVO {

    /** 用户 ID。 */
    private Long id;
    /** 用户名。 */
    private String username;
    /** 真实姓名。 */
    private String realName;
    /** 邮箱。 */
    private String email;
    /** 手机号。 */
    private String phone;
    /** 用户类型：1-管理员，2-普通用户。 */
    private Integer userType;
    /** 状态：0-禁用，1-启用。 */
    private Integer status;
    /** 最后登录时间。 */
    private LocalDateTime lastLoginTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
