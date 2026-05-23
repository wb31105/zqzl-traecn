package com.sso.service;

import com.sso.client.UserServiceClient;
import com.sso.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2 独立配套服务
 * 注意：OAuth2登录流程由 Spring Security OAuth2 Authorization Server 处理
 * 本服务仅提供：注册、忘记密码、验证码等配套功能
 */
@Service
public class AuthService {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    private final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VerifiedUser> verifiedUsers = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS_BEFORE_CAPTCHA = 1;
    private static final long VERIFY_TOKEN_EXPIRE_MINUTES = 15;

    private static class VerifiedUser {
        private final String username;
        private final String email;
        private final String phone;
        private final long timestamp;

        VerifiedUser(String username, String email, String phone) {
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return System.currentTimeMillis() - timestamp < java.util.concurrent.TimeUnit.MINUTES.toMillis(VERIFY_TOKEN_EXPIRE_MINUTES);
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }
    }

    /**
     * @deprecated OAuth2登录流程由 Spring Security 处理，不推荐使用API登录
     */
    @Deprecated
    public LoginResponse login(LoginRequest request) {
        if (shouldShowCaptcha(request.getUsername())) {
            if (request.getCaptcha() == null || request.getCaptchaKey() == null) {
                return new LoginResponse(false, "请输入验证码", null, true, null);
            }
            if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
                return new LoginResponse(false, "验证码错误", null, true, null);
            }
            captchaService.removeCaptcha(request.getCaptchaKey());
        }

        request.setCaptcha(null);
        request.setCaptchaKey(null);

        LoginResponse userResponse = userServiceClient.validateLogin(request);
        
        if (userResponse.isSuccess()) {
            loginAttempts.remove(request.getUsername());
        } else {
            incrementLoginAttempts(request.getUsername());
        }
        
        userResponse.setRequireCaptcha(shouldShowCaptcha(request.getUsername()));
        return userResponse;
    }

    public Map<String, Object> verifyForgotPassword(ForgotPasswordVerifyRequest request) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        
        if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
            result.put("success", false);
            result.put("message", "图形验证码错误");
            return result;
        }
        
        captchaService.removeCaptcha(request.getCaptchaKey());
        
        Map<String, Object> userInfo = userServiceClient.findUserByIdentifier(request.getIdentifier());
        
        if (!(boolean) userInfo.get("success")) {
            result.put("success", false);
            result.put("message", userInfo.get("message"));
            return result;
        }
        
        String username = (String) userInfo.get("username");
        String email = (String) userInfo.get("email");
        String phone = (String) userInfo.get("phone");
        
        String verifyToken = UUID.randomUUID().toString();
        verifiedUsers.put(verifyToken, new VerifiedUser(username, email, phone));
        
        result.put("success", true);
        result.put("message", "验证成功");
        result.put("verifyToken", verifyToken);
        result.put("email", email);
        result.put("phone", phone);
        return result;
    }

    public Map<String, Object> sendVerificationCode(SendVerificationCodeRequest request) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        
        String identifier;
        if (StringUtils.hasText(request.getEmail())) {
            identifier = request.getEmail();
        } else if (StringUtils.hasText(request.getPhone())) {
            identifier = request.getPhone();
        } else {
            result.put("success", false);
            result.put("message", "请输入邮箱或手机号");
            return result;
        }
        
        verificationCodeService.generateAndSendCode(identifier);
        
        result.put("success", true);
        result.put("message", "验证码已发送，有效期5分钟");
        return result;
    }

    public RegisterResponse register(RegisterRequest request) {
        if (!StringUtils.hasText(request.getVerificationCode())) {
            return new RegisterResponse(false, "请输入验证码", null);
        }
        
        if (!verificationCodeService.verifyCode(request.getPhone(), request.getVerificationCode())) {
            return new RegisterResponse(false, "验证码错误或已过期", null);
        }
        
        RegisterResponse response = userServiceClient.register(request);
        return response;
    }

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        VerifiedUser verifiedUser = verifiedUsers.get(request.getVerifyToken());
        if (verifiedUser == null || !verifiedUser.isValid()) {
            return new ForgotPasswordResponse(false, "验证已过期，请重新验证");
        }
        
        request.setUsername(verifiedUser.getUsername());
        request.setEmail(verifiedUser.getEmail());
        request.setPhone(verifiedUser.getPhone());
        
        String identifier;
        if ("email".equals(request.getSelectedContact()) && StringUtils.hasText(verifiedUser.getEmail())) {
            identifier = verifiedUser.getEmail();
        } else if ("phone".equals(request.getSelectedContact()) && StringUtils.hasText(verifiedUser.getPhone())) {
            identifier = verifiedUser.getPhone();
        } else {
            identifier = StringUtils.hasText(verifiedUser.getPhone()) ? 
                verifiedUser.getPhone() : verifiedUser.getEmail();
        }
        
        if (!verificationCodeService.verifyCode(identifier, request.getVerificationCode())) {
            return new ForgotPasswordResponse(false, "验证码错误或已过期");
        }
        
        ForgotPasswordResponse response = userServiceClient.forgotPassword(request);
        
        if (response.isSuccess()) {
            verifiedUsers.remove(request.getVerifyToken());
        }
        
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
}
