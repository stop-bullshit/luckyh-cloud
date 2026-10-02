package com.luckyh.cloud.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 后台维护登录用户请求。
 *
 * @author heng.wang
 * @since 2026-10-02
 */
@Data
public class ManagedUserDTO {

    /** 用户名。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 新建时使用的登录密码；修改时留空表示不修改密码。 */
    private String password;

    /** 真实姓名。 */
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    /** 邮箱。 */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 手机号。 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 用户类型：1-管理员，2-普通用户。 */
    @NotNull(message = "用户类型不能为空")
    private Integer userType;

    /** 状态：0-禁用，1-启用。 */
    @NotNull(message = "用户状态不能为空")
    private Integer status;
}
