package com.cc.ccojbackend.user.controller;

import com.cc.ccojbackend.common.auth.CurrentUser;
import com.cc.ccojbackend.model.user.SessionIdentity;
import org.springframework.session.FindByIndexNameSessionRepository;
import com.cc.ccojbackend.common.result.Result;
import com.cc.ccojbackend.user.model.dto.UserLoginRequest;
import com.cc.ccojbackend.user.model.dto.UserRegisterRequest;
import com.cc.ccojbackend.user.model.vo.UserVO;
import com.cc.ccojbackend.user.service.OjUserService;
import com.cc.ccojbackend.model.user.UserIdentityDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final OjUserService userService;

    /**
     * 注册接口
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {

        Long userId = userService.register(userRegisterRequest);

        return Result.success(userId);
    }

    /**
     * 登录接口
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    public Result<Long> login(@Valid @RequestBody UserLoginRequest userLoginRequest,
                              HttpServletRequest request) {

        // 1. 查询用户、校验密码、检查账号状态
        Long userId = userService.login(userLoginRequest);

        UserVO user = userService.current(userId);

        // 2. 获取当前会话，没有就创建
        HttpSession session = request.getSession();

        // 3. 登录成功后更换会话 ID
        request.changeSessionId();

        // 4. 将用户 ID 保存到当前会话
        session.setAttribute(SessionIdentity.ATTRIBUTE, new SessionIdentity(userId, user.getUserRole(), user.getStatus()));
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString());

        // 5. 返回登陆结果
        return Result.success(userId, "登陆成功");
    }

    /**
     * 实现退出功能
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return Result.success("成功退出登陆");
    }

    /**
     * 获取当前用户
     *
     * @param request
     * @return
     */
    @GetMapping("/current")
    public Result<UserVO> currentUser(HttpServletRequest request) {

        return Result.success(userService.current(CurrentUser.require(request).userId()));

    }

    @GetMapping("/identity")
    public Result<UserIdentityDTO> getCurrentUser(HttpServletRequest request) {
        UserVO currentUser = userService.current(CurrentUser.require(request).userId());
        UserIdentityDTO userIdentityDTO = new UserIdentityDTO();
        userIdentityDTO.setUserRole(currentUser.getUserRole());
        userIdentityDTO.setUserId(currentUser.getId());
        userIdentityDTO.setStatus(currentUser.getStatus());
        return Result.success(userIdentityDTO);
    }
}

