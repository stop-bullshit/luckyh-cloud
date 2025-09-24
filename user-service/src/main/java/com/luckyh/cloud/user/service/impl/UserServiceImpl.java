package com.luckyh.cloud.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
// import com.luckyh.cloud.common.mq.producer.MessageProducer;
// import com.luckyh.cloud.common.mq.message.UserRegisterMessage;
// import com.luckyh.cloud.common.trace.util.TraceUtils;
import com.luckyh.cloud.user.dto.UserDTO;
import com.luckyh.cloud.user.entity.User;
import com.luckyh.cloud.user.mapper.UserMapper;
import com.luckyh.cloud.user.service.UserService;
import com.luckyh.cloud.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    // private final MessageProducer messageProducer;

    @Override
    public Long createUser(UserDTO userDTO) {
        // String traceId = TraceUtils.getTraceId();
        // log.info("开始创建用户，traceId: {}", traceId);
        log.info("开始创建用户");

        User user = new User();
        BeanUtil.copyProperties(userDTO, user);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        boolean saved = save(user);
        if (saved) {
            log.info("用户创建成功，用户ID：{}", user.getId());

            // 发送用户注册消息 - 暂时注释掉，待实现MQ模块
            // UserRegisterMessage message = new UserRegisterMessage();
            // message.setUserId(user.getId());
            // message.setUsername(user.getUsername());
            // message.setEmail(user.getEmail());
            // message.setRegisterTime(user.getCreateTime());
            // message.setTraceId(traceId);

            // messageProducer.sendUserRegisterMessage(message);
            // log.info("用户注册消息已发送，用户ID：{}, traceId: {}", user.getId(), traceId);

            return user.getId();
        }

        log.error("用户创建失败");
        return null;
    }

    @Override
    public boolean updateUser(Long id, UserDTO userDTO) {
        User user = getById(id);
        if (user == null) {
            log.warn("用户不存在，用户ID：{}", id);
            return false;
        }

        BeanUtil.copyProperties(userDTO, user);
        user.setUpdateTime(LocalDateTime.now());

        boolean updated = updateById(user);
        if (updated) {
            log.info("用户更新成功，用户ID：{}", id);
        } else {
            log.error("用户更新失败，用户ID：{}", id);
        }

        return updated;
    }

    @Override
    public boolean deleteUser(Long id) {
        boolean removed = removeById(id);
        if (removed) {
            log.info("用户删除成功，用户ID：{}", id);
        } else {
            log.error("用户删除失败，用户ID：{}", id);
        }

        return removed;
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = getById(id);
        if (user == null) {
            log.warn("用户不存在，用户ID：{}", id);
            return null;
        }

        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public Page<UserVO> getUserPage(Long current, Long size, String username) {
        Page<User> userPage = new Page<>(current, size);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(username)) {
            queryWrapper.like(User::getUsername, username);
        }
        queryWrapper.orderByDesc(User::getCreateTime);

        Page<User> resultPage = page(userPage, queryWrapper);

        Page<UserVO> voPage = new Page<>(current, size, resultPage.getTotal());
        voPage.setRecords(
                resultPage.getRecords().stream()
                        .map(user -> {
                            UserVO userVO = new UserVO();
                            BeanUtil.copyProperties(user, userVO);
                            return userVO;
                        })
                        .toList());

        return voPage;
    }
}
