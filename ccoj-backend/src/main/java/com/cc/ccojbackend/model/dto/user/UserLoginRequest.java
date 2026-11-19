package com.cc.ccojbackend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserLoginRequest {

    /**
     * 登陆账号
     */
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_]{5,8}$",
            message = "账号必须为5～8位字母、数字或下划线")
    private String account;

    /**
     * 登陆密码
     */
    @NotBlank
    @Size(min = 8, max = 12, message = "密码长度必须为8～12位")
    private String password;
}
