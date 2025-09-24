package com.luckyh.cloud.common.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户注册消息
 * 
 * @author Lucky
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRegisterMessage extends BaseMessage {

    private static final long serialVersionUID = 1L;

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
     * 注册时间
     */
    private LocalDateTime registerTime;

    /**
     * 注册来源（WEB, APP, API等）
     */
    private String registerSource;
}