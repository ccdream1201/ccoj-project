package com.cc.ccojbackend.user.model.vo;

import lombok.Data;

@Data
public class UserVO {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 登录账号
     */
    private String account;

    /**
     * 账号状态：0正常，1禁用
     */
    private Integer status;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 个人简介
     */
    private String profile;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色：USER普通用户，ADMIN管理员
     */
    private String userRole;
}
