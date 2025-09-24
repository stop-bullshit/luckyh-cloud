package com.luckyh.cloud.auth.service;

import com.luckyh.cloud.auth.dto.LoginDTO;
import com.luckyh.cloud.auth.dto.RegisterDTO;
import com.luckyh.cloud.auth.vo.LoginVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param loginDTO 登录信息
     * @return 登录结果
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 用户注册
     *
     * @param registerDTO 注册信息
     * @return 是否成功
     */
    boolean register(RegisterDTO registerDTO);

    /**
     * 刷新令牌
     *
     * @param refreshToken 刷新令牌
     * @return 新的访问令牌
     */
    LoginVO refreshToken(String refreshToken);

    /**
     * 退出登录
     *
     * @param token 访问令牌
     * @return 是否成功
     */
    boolean logout(String token);

    /**
     * 验证令牌
     *
     * @param token 访问令牌
     * @return 用户信息
     */
    LoginVO.UserInfo validateToken(String token);
}
