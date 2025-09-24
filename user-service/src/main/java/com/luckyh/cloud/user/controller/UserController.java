package com.luckyh.cloud.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luckyh.cloud.user.common.Result;
import com.luckyh.cloud.user.dto.UserDTO;
import com.luckyh.cloud.user.service.UserService;
import com.luckyh.cloud.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 创建用户
     */
    @PostMapping
    public Result<Long> createUser(@RequestBody @Validated UserDTO userDTO) {
        Long userId = userService.createUser(userDTO);
        if (userId != null) {
            return Result.success("用户创建成功", userId);
        }
        return Result.error("用户创建失败");
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody @Validated UserDTO userDTO) {
        boolean updated = userService.updateUser(id, userDTO);
        if (updated) {
            return Result.success("用户更新成功");
        }
        return Result.error("用户更新失败");
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        if (deleted) {
            return Result.success("用户删除成功");
        }
        return Result.error("用户删除失败");
    }

    /**
     * 根据ID获取用户
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        UserVO userVO = userService.getUserById(id);
        if (userVO != null) {
            return Result.success(userVO);
        }
        return Result.error("用户不存在");
    }

    /**
     * 分页查询用户
     */
    @GetMapping
    public Result<Page<UserVO>> getUserPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String username) {
        Page<UserVO> userPage = userService.getUserPage(current, size, username);
        return Result.success(userPage);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("用户服务正常运行");
    }
}
