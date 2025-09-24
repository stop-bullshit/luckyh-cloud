package com.luckyh.cloud.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luckyh.cloud.auth.dto.LoginDTO;
import com.luckyh.cloud.auth.dto.RegisterDTO;
import com.luckyh.cloud.auth.entity.SysUser;
import com.luckyh.cloud.auth.entity.SysUserRole;
import com.luckyh.cloud.auth.mapper.SysUserMapper;
import com.luckyh.cloud.auth.mapper.SysUserRoleMapper;
import com.luckyh.cloud.auth.service.AuthService;
import com.luckyh.cloud.common.web.JwtUtils;
import com.luckyh.cloud.common.redis.RedisUtils;
import com.luckyh.cloud.auth.vo.LoginVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private JwtUtils jwtUtils;
    @Resource
    private RedisUtils redisUtils;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 查询用户
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, loginDTO.getUsername())
                .eq(SysUser::getStatus, 1);
        SysUser sysUser = sysUserMapper.selectOne(queryWrapper);

        if (sysUser == null) {
            throw new RuntimeException("用户不存在或已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), sysUser.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 更新登录时间
        sysUser.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(sysUser);

        // 生成令牌
        return generateLoginVO(sysUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean register(RegisterDTO registerDTO) {
        // 验证确认密码
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        // 检查用户名是否存在
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, registerDTO.getUsername());
        if (sysUserMapper.selectCount(queryWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查邮箱是否存在
        if (StrUtil.isNotBlank(registerDTO.getEmail())) {
            queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysUser::getEmail, registerDTO.getEmail());
            if (sysUserMapper.selectCount(queryWrapper) > 0) {
                throw new RuntimeException("邮箱已被注册");
            }
        }

        // 创建用户
        SysUser sysUser = new SysUser();
        BeanUtil.copyProperties(registerDTO, sysUser);
        sysUser.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        sysUser.setStatus(1);
        sysUser.setCreateTime(LocalDateTime.now());
        sysUser.setUpdateTime(LocalDateTime.now());

        int result = sysUserMapper.insert(sysUser);
        if (result > 0) {
            // 分配默认角色（普通用户角色ID为2）
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(sysUser.getId());
            userRole.setRoleId(registerDTO.getUserType() == 1 ? 1L : 2L);
            sysUserRoleMapper.insert(userRole);

            log.info("用户注册成功，用户ID：{}", sysUser.getId());
            return true;
        }

        log.error("用户注册失败");
        return false;
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        // 验证刷新令牌
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new RuntimeException("刷新令牌无效或已过期");
        }

        String username = jwtUtils.getUsernameFromToken(refreshToken);
        Long userId = jwtUtils.getUserIdFromToken(refreshToken);

        // 查询用户
        SysUser sysUser = sysUserMapper.selectById(userId);
        if (sysUser == null || !sysUser.getUsername().equals(username)) {
            throw new RuntimeException("用户不存在");
        }

        // 生成新的令牌
        return generateLoginVO(sysUser);
    }

    @Override
    public boolean logout(String token) {
        try {
            // 将令牌加入黑名单
            String username = jwtUtils.getUsernameFromToken(token);
            if (StrUtil.isNotBlank(username)) {
                redisUtils.addTokenToBlacklist(token, jwtUtils.getExpiration());
                log.info("用户 {} 退出登录成功", username);
                return true;
            }
        } catch (Exception e) {
            log.error("退出登录失败", e);
        }
        return false;
    }

    @Override
    public LoginVO.UserInfo validateToken(String token) {
        // 检查令牌是否在黑名单中
        if (redisUtils.isTokenInBlacklist(token)) {
            throw new RuntimeException("令牌已失效");
        }

        // 验证令牌
        if (!jwtUtils.validateToken(token)) {
            throw new RuntimeException("令牌无效或已过期");
        }

        Long userId = jwtUtils.getUserIdFromToken(token);
        SysUser sysUser = sysUserMapper.selectById(userId);

        if (sysUser == null || sysUser.getStatus() != 1) {
            throw new RuntimeException("用户不存在或已被禁用");
        }

        LoginVO.UserInfo userInfo = new LoginVO.UserInfo();
        BeanUtil.copyProperties(sysUser, userInfo);
        return userInfo;
    }

    /**
     * 生成登录响应
     */
    private LoginVO generateLoginVO(SysUser sysUser) {
        // 获取用户角色和权限
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(sysUser.getId());
        List<String> permissions = sysUserMapper.selectPermissionsByUserId(sysUser.getId());

        // 构建JWT Claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", sysUser.getUserType());
        claims.put("roles", roles);
        claims.put("permissions", permissions);

        // 生成令牌
        String accessToken = jwtUtils.generateToken(sysUser.getUsername(), sysUser.getId(), claims);
        String refreshToken = jwtUtils.generateRefreshToken(sysUser.getUsername(), sysUser.getId());

        // 构建响应
        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setExpiresIn(jwtUtils.getExpiration());
        loginVO.setRoles(roles);
        loginVO.setPermissions(permissions);

        // 用户信息
        LoginVO.UserInfo userInfo = new LoginVO.UserInfo();
        BeanUtil.copyProperties(sysUser, userInfo);
        loginVO.setUserInfo(userInfo);

        return loginVO;
    }
}
