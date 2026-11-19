package com.cc.ccojbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.mapper.OjUserMapper;
import com.cc.ccojbackend.model.dto.user.UserLoginRequest;
import com.cc.ccojbackend.model.dto.user.UserRegisterRequest;
import com.cc.ccojbackend.model.entity.OjUser;
import com.cc.ccojbackend.model.vo.user.UserVO;
import com.cc.ccojbackend.service.OjUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
* @author jiangchengchuan
* @description 针对表【oj_user(用户表)】的数据库操作Service实现
* @createDate 2026-11-01 15:41:13
*/
@Service
public class OjUserServiceImpl extends ServiceImpl<OjUserMapper, OjUser>
    implements OjUserService{

    private final PasswordEncoder passwordEncoder;

    public OjUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 实现用户注册
     * @param userRegisterRequest 注册请求
     * @return 返回生成的用户id
     */
    @Override
    public Long register(UserRegisterRequest userRegisterRequest) {

        String account = userRegisterRequest.getAccount();
        String password = userRegisterRequest.getPassword();
        String confirmPassword = userRegisterRequest.getConfirmPassword();

        // 1. 检验密码与确认密码是否相同
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(),
                    "两次输入的密码不一致");
        }
        // 2. 账号是否已经创建
        QueryWrapper<OjUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        OjUser ojUser = baseMapper.selectOne(queryWrapper);
        if (ojUser != null) {
            throw new BusinessException(ResultCode.ACCOUNT_EXISTS);
        }
        // 账号未创建
        // 3. 生成密码哈希
        String passwordHash = passwordEncoder.encode(password);
        // 4. 创建用户
        ojUser = new OjUser();
        ojUser.setAccount(account);
        ojUser.setPasswordHash(passwordHash);

        try {
            int rows = baseMapper.insert(ojUser);
            if (rows != 1) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
        }
        catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ACCOUNT_EXISTS);
        }
        return ojUser.getId();
    }

    /**
     * 实现用户登陆
     * @param userLoginRequest 登陆请求
     * @return 返回登陆的用户id
     */
    @Override
    public Long login(UserLoginRequest userLoginRequest) {

        String account = userLoginRequest.getAccount();
        String password = userLoginRequest.getPassword();

        // 1. 用户是否存在
        QueryWrapper<OjUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);

        OjUser ojUser = baseMapper.selectOne(queryWrapper);
        if (ojUser == null) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_EXISTS);
        }
        // 2. 密码匹配
        if (!passwordEncoder.matches(password, ojUser.getPasswordHash())) {
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }
        // 3. 账号状态是否正常
        if (ojUser.getStatus() == 1) {
            throw new BusinessException(ResultCode.ACCOUNT_STATUS_ERROR);
        }

        // 4. 返回用户 ID
        return ojUser.getId();

        // 5. 在controller层实现对session的处理
    }

    @Override
    public UserVO current(Long userId) {
        // 用户id为空
        if (userId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }

        OjUser ojUser = baseMapper.selectById(userId);
        // 有id，但是用户不存在，比如逻辑删除
        if (ojUser == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
        // 用户状态异常
        if (ojUser.getStatus() == 1) {
            throw new BusinessException(ResultCode.ACCOUNT_STATUS_ERROR);
        }
        // 一切正常，返回userVO
        UserVO current = new UserVO();

        current.setId(ojUser.getId());
        current.setAccount(ojUser.getAccount());
        current.setNickname(ojUser.getNickname());
        current.setAvatarUrl(ojUser.getAvatarUrl());
        current.setProfile(ojUser.getProfile());
        current.setPhone(ojUser.getPhone());
        current.setEmail(ojUser.getEmail());
        current.setUserRole(ojUser.getUserRole());

        return current;
    }

}




