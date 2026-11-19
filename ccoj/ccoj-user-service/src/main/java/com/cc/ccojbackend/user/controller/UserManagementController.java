package com.cc.ccojbackend.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc.ccojbackend.common.auth.AuthCheck;
import com.cc.ccojbackend.common.auth.CurrentUser;
import com.cc.ccojbackend.common.enums.UserRole;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.Result;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.user.model.entity.OjUser;
import com.cc.ccojbackend.user.service.OjUserService;
import com.cc.ccojbackend.user.service.SessionRevocation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserManagementController {
    private final OjUserService users;
    private final SessionRevocation sessions;

    public record ProfileUpdate(@NotBlank @Size(max = 64) String nickname,
                                @Size(max = 512) String avatarUrl,
                                @Size(max = 1000) String profile) {}

    public record AccountUpdate(@NotBlank String role, @NotNull @Min(0) @Max(1) Integer status) {}

    public record AccountView(Long id, String account, String nickname, String role, Integer status) {
        static AccountView from(OjUser user) {
            return new AccountView(user.getId(), user.getAccount(), user.getNickname(),
                    user.getUserRole(), user.getStatus());
        }
    }

    public record AccountPage(List<AccountView> records, long total) {}

    @PatchMapping("/me")
    @AuthCheck
    public Result<Void> updateProfile(@Valid @RequestBody ProfileUpdate input, HttpServletRequest request) {
        OjUser update = new OjUser();
        update.setId(CurrentUser.require(request).userId());
        update.setNickname(input.nickname());
        update.setAvatarUrl(input.avatarUrl());
        update.setProfile(input.profile());
        if (!users.updateById(update)) throw new BusinessException(ResultCode.NOT_FOUND);
        return Result.success();
    }

    @GetMapping("/admin")
    @AuthCheck(mustRole = "ADMIN")
    public Result<AccountPage> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException(ResultCode.PARAMS_ERROR);
        long offset = (page - 1L) * size;
        var records = users.list(new LambdaQueryWrapper<OjUser>().orderByDesc(OjUser::getId)
                .last("LIMIT " + size + " OFFSET " + offset));
        return Result.success(new AccountPage(records.stream().map(AccountView::from).toList(), users.count()));
    }

    @PatchMapping("/admin/{id}")
    @AuthCheck(mustRole = "ADMIN")
    public Result<Void> updateAccount(@PathVariable Long id, @Valid @RequestBody AccountUpdate input) {
        if (!UserRole.ADMIN.getValue().equals(input.role()) && !UserRole.USER.getValue().equals(input.role())) {
            throw new BusinessException(ResultCode.PARAMS_ERROR);
        }
        OjUser update = new OjUser();
        update.setId(id);
        update.setUserRole(input.role());
        update.setStatus(input.status());
        if (!users.updateById(update)) throw new BusinessException(ResultCode.NOT_FOUND);
        sessions.revoke(id);
        return Result.success();
    }

    @DeleteMapping("/admin/{id}")
    @AuthCheck(mustRole = "ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        if (!users.removeById(id)) throw new BusinessException(ResultCode.NOT_FOUND);
        sessions.revoke(id);
        return Result.success();
    }
}
