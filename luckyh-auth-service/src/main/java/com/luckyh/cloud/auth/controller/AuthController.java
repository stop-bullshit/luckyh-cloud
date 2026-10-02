package com.luckyh.cloud.auth.controller;

import com.luckyh.cloud.common.core.domain.Result;
import com.luckyh.cloud.common.core.domain.R;
import com.luckyh.cloud.auth.dto.LoginDTO;
import com.luckyh.cloud.auth.dto.ManagedUserDTO;
import com.luckyh.cloud.auth.dto.RegisterDTO;
import com.luckyh.cloud.auth.service.AuthService;
import com.luckyh.cloud.auth.vo.LoginVO;
import com.luckyh.cloud.auth.vo.ManagedUserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
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
        LoginVO loginVO = authService.login(loginDTO);
        return R.success("登录成功", loginVO);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated RegisterDTO registerDTO) {
        boolean success = authService.register(registerDTO);
        if (success) {
            return Result.success("注册成功");
        }
        return Result.error("注册失败");
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        LoginVO loginVO = authService.refreshToken(refreshToken);
        return Result.success("令牌刷新成功", loginVO);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        boolean success = authService.logout(token);
        if (success) {
            return Result.success("退出登录成功");
        }
        return Result.error("退出登录失败");
    }

    /**
     * 验证令牌
     */
    @GetMapping("/validate")
    public Result<LoginVO.UserInfo> validateToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        LoginVO.UserInfo userInfo = authService.validateToken(token);
        return Result.success(userInfo);
    }

    /** 查询真实登录用户。 */
    @GetMapping("/users")
    public Result<IPage<ManagedUserVO>> getUsers(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username) {
        // 逻辑变动: 用户管理接入认证用户-20261002-1710-01
        return Result.success(authService.getUserPage(current, size, username));
    }

    /** 批量查询登录用户，供订单分页一次关联。 */
    @GetMapping("/users/batch")
    public Result<List<ManagedUserVO>> getUsersByIds(@RequestParam List<Long> ids) {
        return Result.success(authService.getUsersByIds(ids));
    }

    /** 查询登录用户详情。 */
    @GetMapping("/users/{id}")
    public Result<ManagedUserVO> getUser(@PathVariable Long id) {
        ManagedUserVO user = authService.getUser(id);
        return user == null ? Result.error(404, "用户不存在") : Result.success(user);
    }

    /** 新建登录用户。 */
    @PostMapping("/users")
    public Result<Long> createUser(@RequestBody @Validated ManagedUserDTO userDTO) {
        return Result.success(authService.createUser(userDTO));
    }

    /** 修改登录用户。 */
    @PutMapping("/users/{id}")
    public Result<String> updateUser(@PathVariable Long id,
                                     @RequestBody @Validated ManagedUserDTO userDTO) {
        return authService.updateUser(id, userDTO)
                ? Result.success("用户更新成功") : Result.error(404, "用户不存在");
    }

    /** 删除登录用户，禁止删除当前登录账户。 */
    @DeleteMapping("/users/{id}")
    public Result<String> deleteUser(@PathVariable Long id,
                                     @RequestHeader("Authorization") String authHeader) {
        LoginVO.UserInfo currentUser = authService.validateToken(authHeader.replace("Bearer ", ""));
        if (id.equals(currentUser.getId())) {
            return Result.error(409, "不能删除当前登录账户");
        }
        return authService.deleteUser(id)
                ? Result.success("用户删除成功") : Result.error(404, "用户不存在");
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("认证服务正常运行");
    }
}
