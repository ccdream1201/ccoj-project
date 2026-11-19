package com.cc.ccojbackend.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.user.SessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CurrentUserTest {

    @Test
    void rejectsAnonymousRequestWithoutCreatingASession() {
        var request = new MockHttpServletRequest();
        assertEquals(ResultCode.NOT_LOGIN.getCode(),
                assertThrows(BusinessException.class, () -> CurrentUser.require(request)).getCode());
        assertNull(request.getSession(false));
    }

    @Test
    void rejectsSessionWithInvalidIdentity() {
        var request = requestWithIdentity("not-an-identity");
        assertEquals(ResultCode.NOT_LOGIN.getCode(),
                assertThrows(BusinessException.class, () -> CurrentUser.require(request)).getCode());
    }

    @Test
    void rejectsDisabledAccount() {
        var request = requestWithIdentity(new SessionIdentity(42L, "ADMIN", 1));
        assertEquals(ResultCode.ACCOUNT_STATUS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> CurrentUser.require(request)).getCode());
    }

    @Test
    void returnsActiveIdentityFromSession() {
        var identity = new SessionIdentity(42L, "USER", 0);
        assertSame(identity, CurrentUser.require(requestWithIdentity(identity)));
    }

    private static MockHttpServletRequest requestWithIdentity(Object identity) {
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute(SessionIdentity.ATTRIBUTE, identity);
        return request;
    }
}
