package com.cc.ccojbackend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 */
@Data
public class UserRegisterRequest {

    /**
     * 登录账号
     */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{5,8}$",
            message = "账号必须为5～8位字母、数字或下划线")
    private String account;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 12, message = "密码长度必须为8～12位")
    private String password;

    /**
     * 确认密码
     */
    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, max = 12, message = "确认密码长度必须为8～12位")
    private String confirmPassword;

}
