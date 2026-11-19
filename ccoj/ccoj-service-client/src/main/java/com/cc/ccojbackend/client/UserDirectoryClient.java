package com.cc.ccojbackend.client;

import com.cc.ccojbackend.model.user.UserSummary;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

public interface UserDirectoryClient {
    @GetExchange("/inner/users/{id}")
    UserSummary get(@PathVariable("id") long id);
}
