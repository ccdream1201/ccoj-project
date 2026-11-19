package com.cc.ccojbackend.common.auth;

import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.user.SessionIdentity;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentUser {
    private CurrentUser() {}

    public static SessionIdentity require(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(SessionIdentity.ATTRIBUTE) instanceof SessionIdentity user)
                || user.userId() == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
        if (!Integer.valueOf(0).equals(user.status())) {
            throw new BusinessException(ResultCode.ACCOUNT_STATUS_ERROR);
        }
        return user;
    }
}
