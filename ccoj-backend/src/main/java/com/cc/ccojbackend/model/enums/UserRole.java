package com.cc.ccojbackend.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {

    USER("USER", "普通用户"),
    ADMIN("ADMIN", "管理员");

    private final String value;
    private final String text;
}
