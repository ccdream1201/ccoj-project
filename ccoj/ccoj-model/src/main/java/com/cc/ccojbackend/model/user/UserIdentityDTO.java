package com.cc.ccojbackend.model.user;

import lombok.Data;

@Data
public class UserIdentityDTO {

    Long userId;

    Integer status;

    String userRole;
}
