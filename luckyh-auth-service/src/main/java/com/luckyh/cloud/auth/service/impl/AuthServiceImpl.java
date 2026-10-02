package com.luckyh.cloud.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.luckyh.cloud.auth.dto.LoginDTO;
import com.luckyh.cloud.auth.dto.ManagedUserDTO;
import com.luckyh.cloud.auth.dto.RegisterDTO;
import com.luckyh.cloud.auth.entity.SysUser;
import com.luckyh.cloud.auth.entity.SysUserRole;
import com.luckyh.cloud.auth.mapper.SysUserMapper;
import com.luckyh.cloud.auth.mapper.SysUserRoleMapper;
import com.luckyh.cloud.auth.service.AuthService;
import com.luckyh.cloud.auth.util.JwtUtils;
import com.luckyh.cloud.common.core.exception.BusinessException;
import com.luckyh.cloud.common.core.exception.ServiceException;
import com.luckyh.cloud.common.redis.RedisUtils;
import com.luckyh.cloud.auth.vo.LoginVO;
import com.luckyh.cloud.auth.vo.ManagedUserVO;
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
        // 逻辑变动: 明确认证业务拒绝与服务故障边界，避免将内部异常回显给前端-20261002-2117-01
        // 查询用户
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, loginDTO.getUsername())
                .eq(SysUser::getStatus, 1);
        SysUser sysUser = sysUserMapper.selectOne(queryWrapper);

        if (sysUser == null) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), sysUser.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
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
            throw new BusinessException(400, "两次输入的密码不一致");
        }

        // 检查用户名是否存在
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, registerDTO.getUsername());
        if (sysUserMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        // 检查邮箱是否存在
        if (StrUtil.isNotBlank(registerDTO.getEmail())) {
            queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysUser::getEmail, registerDTO.getEmail());
            if (sysUserMapper.selectCount(queryWrapper) > 0) {
                throw new BusinessException(409, "邮箱已被注册");
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
            throw new BusinessException(401, "刷新令牌无效或已过期");
        }

        String username = jwtUtils.getUsernameFromToken(refreshToken);
        Long userId = jwtUtils.getUserIdFromToken(refreshToken);

        // 查询用户
        SysUser sysUser = sysUserMapper.selectById(userId);
        if (sysUser == null || !sysUser.getUsername().equals(username)) {
            throw new BusinessException(401, "用户不存在");
        }

        // 生成新的令牌
        return generateLoginVO(sysUser);
    }

    @Override
    public boolean logout(String token) {
        if (!jwtUtils.validateToken(token)) {
            throw new BusinessException(401, "令牌无效或已过期");
        }
        String username = jwtUtils.getUsernameFromToken(token);
        if (StrUtil.isBlank(username)) {
            throw new BusinessException(401, "令牌无效或已过期");
        }
        if (!redisUtils.addTokenToBlacklist(token, jwtUtils.getExpiration())) {
            throw new ServiceException(503, "退出登录服务暂不可用");
        }
        log.info("用户 {} 退出登录成功", username);
        return true;
    }

    @Override
    public LoginVO.UserInfo validateToken(String token) {
        // 检查令牌是否在黑名单中
        if (redisUtils.isTokenInBlacklist(token)) {
            throw new BusinessException(401, "令牌已失效");
        }

        // 验证令牌
        if (!jwtUtils.validateToken(token)) {
            throw new BusinessException(401, "令牌无效或已过期");
        }

        Long userId = jwtUtils.getUserIdFromToken(token);
        SysUser sysUser = sysUserMapper.selectById(userId);

        if (sysUser == null || sysUser.getStatus() != 1) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        LoginVO.UserInfo userInfo = new LoginVO.UserInfo();
        BeanUtil.copyProperties(sysUser, userInfo);
        return userInfo;
    }

    @Override
    public IPage<ManagedUserVO> getUserPage(long current, long size, String username) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .like(StrUtil.isNotBlank(username), SysUser::getUsername, username)
                .orderByDesc(SysUser::getId);
        return sysUserMapper.selectPage(new Page<>(current, size), query)
                .convert(user -> BeanUtil.copyProperties(user, ManagedUserVO.class));
    }

    @Override
    public ManagedUserVO getUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        return user == null ? null : BeanUtil.copyProperties(user, ManagedUserVO.class);
    }

    @Override
    public List<ManagedUserVO> getUsersByIds(List<Long> ids) {
        // 逻辑变动: 订单分页批量关联登录用户-20261002-1735-01
        return sysUserMapper.selectBatchIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, ManagedUserVO.class))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(ManagedUserDTO userDTO) {
        if (StrUtil.isBlank(userDTO.getPassword())) {
            throw new BusinessException(400, "新建用户时密码不能为空");
        }
        checkUniqueUser(userDTO, null);
        SysUser user = new SysUser();
        BeanUtil.copyProperties(userDTO, user, "password");
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(user.getUserType() == 1 ? 1L : 2L);
        sysUserRoleMapper.insert(userRole);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(Long id, ManagedUserDTO userDTO) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            return false;
        }
        boolean userTypeChanged = !user.getUserType().equals(userDTO.getUserType());
        checkUniqueUser(userDTO, id);
        BeanUtil.copyProperties(userDTO, user, "password");
        if (StrUtil.isNotBlank(userDTO.getPassword())) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        user.setUpdateTime(LocalDateTime.now());
        if (sysUserMapper.updateById(user) != 1) {
            return false;
        }
        if (userTypeChanged) {
            sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(id);
            userRole.setRoleId(user.getUserType() == 1 ? 1L : 2L);
            sysUserRoleMapper.insert(userRole);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long id) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        return sysUserMapper.deleteById(id) == 1;
    }

    private void checkUniqueUser(ManagedUserDTO userDTO, Long excludedId) {
        LambdaQueryWrapper<SysUser> usernameQuery = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, userDTO.getUsername())
                .ne(excludedId != null, SysUser::getId, excludedId);
        if (sysUserMapper.selectCount(usernameQuery) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (StrUtil.isBlank(userDTO.getEmail())) {
            return;
        }
        LambdaQueryWrapper<SysUser> emailQuery = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, userDTO.getEmail())
                .ne(excludedId != null, SysUser::getId, excludedId);
        if (sysUserMapper.selectCount(emailQuery) > 0) {
            throw new BusinessException(409, "邮箱已被注册");
        }
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
