package com.luckyh.cloud.auth.controller;

import com.luckyh.cloud.auth.common.Result;
import com.luckyh.cloud.common.core.domain.R;
import com.luckyh.cloud.auth.dto.LoginDTO;
import com.luckyh.cloud.auth.dto.RegisterDTO;
import com.luckyh.cloud.auth.service.AuthService;
import com.luckyh.cloud.auth.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Validated LoginDTO loginDTO) {
        try {
            LoginVO loginVO = authService.login(loginDTO);
            return R.success("登录成功", loginVO);
        } catch (Exception e) {
            log.error("登录失败", e);
            return R.unauthorized(e.getMessage());
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated RegisterDTO registerDTO) {
        try {
            boolean success = authService.register(registerDTO);
            if (success) {
                return Result.success("注册成功");
            }
            return Result.error("注册失败");
        } catch (Exception e) {
            log.error("注册失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        try {
            LoginVO loginVO = authService.refreshToken(refreshToken);
            return Result.success("令牌刷新成功", loginVO);
        } catch (Exception e) {
            log.error("令牌刷新失败", e);
            return Result.unauthorized(e.getMessage());
        }
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            boolean success = authService.logout(token);
            if (success) {
                return Result.success("退出登录成功");
            }
            return Result.error("退出登录失败");
        } catch (Exception e) {
            log.error("退出登录失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 验证令牌
     */
    @GetMapping("/validate")
    public Result<LoginVO.UserInfo> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            LoginVO.UserInfo userInfo = authService.validateToken(token);
            return Result.success(userInfo);
        } catch (Exception e) {
            log.error("令牌验证失败", e);
            return Result.unauthorized(e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("认证服务正常运行");
    }
}
