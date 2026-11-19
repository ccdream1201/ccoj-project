package com.cc.ccojbackend.auth;

import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.vo.user.UserVO;
import com.cc.ccojbackend.service.OjUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginUserProvider {

    private final OjUserService userService;

    public UserVO getCurrentUser(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        // 1. 未登陆
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }

        Long userId = (Long) session.getAttribute("LOGIN_USER_ID");

        try {
            return userService.current(userId);
        } catch (BusinessException e) {
            if (ResultCode.NOT_LOGIN.getCode().equals(e.getCode())
                    || ResultCode.ACCOUNT_STATUS_ERROR.getCode().equals(e.getCode())) {
                session.invalidate();
            }
            throw e;
        }
    }
}
