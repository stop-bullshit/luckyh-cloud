package com.luckyh.cloud.common.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户注册消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRegisterMessage extends BaseMessage {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 注册时间
     */
    private LocalDateTime registerTime;

    public UserRegisterMessage() {
        super("USER_REGISTER", "auth-service");
    }

    public UserRegisterMessage(Long userId, String username, String email, String phone, String realName) {
        this();
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.realName = realName;
    }
}
