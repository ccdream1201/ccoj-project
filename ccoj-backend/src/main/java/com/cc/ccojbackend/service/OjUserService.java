package com.cc.ccojbackend.service;

import com.cc.ccojbackend.model.dto.user.UserLoginRequest;
import com.cc.ccojbackend.model.dto.user.UserRegisterRequest;
import com.cc.ccojbackend.model.entity.OjUser;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cc.ccojbackend.model.vo.user.UserVO;

/**
* @author jiangchengchuan
* @description 针对表【oj_user(用户表)】的数据库操作Service
* @createDate 2026-11-01 15:41:13
*/
public interface OjUserService extends IService<OjUser> {

    /**
     * 实现用户登陆
     */
    Long register(UserRegisterRequest userRegisterRequest);

    /**
     * 实现用户登陆
     */
    Long login(UserLoginRequest userLoginRequest);

    /**
     * 查询当前用户
     * @return
     */
    UserVO current(Long userId);
}
