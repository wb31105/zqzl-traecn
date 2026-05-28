package com.sso.config;

import com.sso.client.UserServiceClient;
import com.sso.dto.LoginRequest;
import com.sso.dto.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GrpcAuthenticationProvider implements AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(GrpcAuthenticationProvider.class);

    private final UserServiceClient userServiceClient;

    public GrpcAuthenticationProvider(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = "";
        if (authentication.getCredentials() != null) {
            password = authentication.getCredentials().toString();
        }

        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);

        LoginResponse response;
        try {
            response = userServiceClient.validateLogin(request);
        } catch (Exception e) {
            logger.error("认证服务调用异常, username: {}, error: {}", username, e.getMessage(), e);
            throw new InternalAuthenticationServiceException("认证服务暂时不可用，请稍后重试", e);
        }

        if (response == null) {
            logger.error("认证服务返回空响应, username: {}", username);
            throw new InternalAuthenticationServiceException("认证服务异常，请稍后重试");
        }

        if (!response.isSuccess()) {
            String errorMsg = response.getMessage() != null ? response.getMessage() : "用户名或密码错误";
            logger.warn("用户认证失败, username: {}, message: {}", username, errorMsg);
            throw new BadCredentialsException(errorMsg);
        }

        UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(
                username,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
