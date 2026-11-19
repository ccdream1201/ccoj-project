package com.cc.ccojbackend.common.auth;

import com.cc.ccojbackend.common.constant.InternalAccess;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebAccessConfig implements WebMvcConfigurer {

    private final byte[] key;

    public WebAccessConfig(@Value("${CCOJ_INTERNAL_KEY:}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String provided = request.getHeader(InternalAccess.HEADER);
                if (key.length == 0 || provided == null
                        || !MessageDigest.isEqual(key, provided.getBytes(StandardCharsets.UTF_8))) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/inner/**").order(0);

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method) {
                    AuthCheck check = method.getMethodAnnotation(AuthCheck.class);
                    if (check != null) {
                        var user = CurrentUser.require(request);
                        if (!check.mustRole().isEmpty() && !check.mustRole().equals(user.userRole())) {
                            throw new BusinessException(ResultCode.NO_PERMISSION);
                        }
                    }
                }
                return true;
            }
        }).order(1);
    }
}
