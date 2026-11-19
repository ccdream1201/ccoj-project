package com.cc.ccojbackend.user.service;

import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionRevocation {
    private final RedisIndexedSessionRepository sessions;

    public SessionRevocation(RedisIndexedSessionRepository sessions) {
        this.sessions = sessions;
    }

    public void revoke(Long userId) {
        sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
    }
}
