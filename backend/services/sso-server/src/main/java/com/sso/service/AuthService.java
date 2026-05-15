package com.sso.service;

import com.sso.client.UserServiceClient;
import com.sso.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private CaptchaService captchaService;

    private final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS_BEFORE_CAPTCHA = 3;

    public LoginResponse login(LoginRequest request) {
        if (shouldShowCaptcha(request.getUsername())) {
            if (request.getCaptcha() == null || request.getCaptchaKey() == null) {
                return new LoginResponse(false, "请输入验证码", null, true, null);
            }
            if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
                return new LoginResponse(false, "验证码错误", null, true, null);
            }
        }

        LoginResponse userResponse = userServiceClient.validateLogin(request);
        
        if (userResponse.isSuccess()) {
            loginAttempts.remove(request.getUsername());
            captchaService.removeCaptcha(request.getCaptchaKey());
            
            String ticket = ticketService.generateTicket(userResponse.getUsername());
            return new LoginResponse(
                true,
                "登录成功",
                ticket,
                false,
                userResponse.getUsername()
            );
        } else {
            incrementLoginAttempts(request.getUsername());
            captchaService.removeCaptcha(request.getCaptchaKey());
        }
        
        userResponse.setRequireCaptcha(shouldShowCaptcha(request.getUsername()));
        return userResponse;
    }

    public RegisterResponse register(RegisterRequest request) {
        if (request.getCaptcha() == null || request.getCaptchaKey() == null) {
            return new RegisterResponse(false, "请输入验证码");
        }
        if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
            return new RegisterResponse(false, "验证码错误");
        }
        
        RegisterResponse response = userServiceClient.register(request);
        captchaService.removeCaptcha(request.getCaptchaKey());
        return response;
    }

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        if (request.getCaptcha() == null || request.getCaptchaKey() == null) {
            return new ForgotPasswordResponse(false, "请输入验证码");
        }
        if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
            return new ForgotPasswordResponse(false, "验证码错误");
        }
        
        ForgotPasswordResponse response = userServiceClient.forgotPassword(request);
        captchaService.removeCaptcha(request.getCaptchaKey());
        return response;
    }

    public boolean shouldShowCaptcha(String username) {
        if (username == null) return false;
        return loginAttempts.getOrDefault(username, 0) >= MAX_ATTEMPTS_BEFORE_CAPTCHA;
    }

    private void incrementLoginAttempts(String username) {
        if (username == null) return;
        loginAttempts.merge(username, 1, Integer::sum);
    }

    public Map<String, String> getCaptcha() {
        return captchaService.generateCaptcha();
    }

    public String validateTicket(String ticket) {
        return ticketService.validateTicket(ticket);
    }
}
