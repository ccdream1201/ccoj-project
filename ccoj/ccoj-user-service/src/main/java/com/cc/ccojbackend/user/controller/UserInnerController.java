package com.cc.ccojbackend.user.controller;

import com.cc.ccojbackend.model.user.UserSummary;
import com.cc.ccojbackend.user.service.OjUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/inner/users")
@RequiredArgsConstructor
public class UserInnerController {
    private final OjUserService users;

    @GetMapping("/{id}")
    public ResponseEntity<UserSummary> get(@PathVariable Long id) {
        var user = users.getById(id);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new UserSummary(user.getId(), user.getNickname(), user.getAvatarUrl()));
    }
}
