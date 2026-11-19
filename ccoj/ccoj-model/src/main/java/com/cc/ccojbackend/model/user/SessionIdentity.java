package com.cc.ccojbackend.model.user;

import java.io.Serializable;

public record SessionIdentity(Long userId, String userRole, Integer status) implements Serializable {
    public static final String ATTRIBUTE = "CCOJ_SESSION_IDENTITY";
}
