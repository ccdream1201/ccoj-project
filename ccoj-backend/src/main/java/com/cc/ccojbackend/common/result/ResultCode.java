package com.cc.ccojbackend.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "执行成功"),
    SYSTEM_ERROR(40400, "系统异常"),
    ACCOUNT_EXISTS(11, "账号已存在"),
    ACCOUNT_NOT_EXISTS(11, "账号不存在"),
    PARAMS_ERROR(40000, "请求参数错误"),
    PASSWORD_INCORRECT(111, "密码错误"),
    ACCOUNT_STATUS_ERROR(222, "账号状态异常"),
    NO_PERMISSION(40300, "无操作权限"),
    NOT_FOUND(40401, "记录不存在"),
    NOT_LOGIN(1111, "未登录");

    /**
     * 结果码
     */
    private Integer code;

    /**
     * 结果信息
     */
    private String message;

    private ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
