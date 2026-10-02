package com.luckyh.cloud.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.luckyh.cloud.user.dto.UserDTO;
import com.luckyh.cloud.user.entity.User;
import com.luckyh.cloud.user.vo.UserVO;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 创建用户
     *
     * @param userDTO 用户DTO
     * @return 用户ID
     */
    Long createUser(UserDTO userDTO);

    /**
     * 更新用户
     *
     * @param id      用户ID
     * @param userDTO 用户DTO
     * @return 是否成功
     */
    boolean updateUser(Long id, UserDTO userDTO);

    /**
     * 删除用户
     *
     * @param id 用户ID
     * @return 是否成功
     */
    boolean deleteUser(Long id);

    /**
     * 根据ID获取用户
     *
     * @param id 用户ID
     * @return 用户VO
     */
    UserVO getUserById(Long id);

    /**
     * 分页查询用户
     *
     * @param current  当前页
     * @param size     每页大小
     * @param username 用户名（可选）
     * @return 分页结果
     */
    Page<UserVO> getUserPage(Long current, Long size, String username);
}
