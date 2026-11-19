package com.cc.ccojbackend.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.cc.ccojbackend.user.model.dto.UserLoginRequest;
import com.cc.ccojbackend.user.model.dto.UserRegisterRequest;
import com.cc.ccojbackend.user.model.entity.OjUser;
import com.cc.ccojbackend.user.model.vo.UserVO;
import com.cc.ccojbackend.model.user.UserIdentityDTO;
import jakarta.servlet.http.HttpSession;

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
