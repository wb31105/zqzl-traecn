package com.sso.controller;

import com.sso.dto.*;
import com.sso.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * OAuth2 认证配套接口
 * 
 * 注意：OAuth2 标准登录流程为：
 * 1. 前端跳转 /oauth2/authorize 授权端点
 * 2. Spring Security 显示 /login 页面（Thymeleaf模板）
 * 3. 用户提交表单后，Spring Security 创建 Session
 * 4. 生成 code 回调客户端
 * 5. 客户端后端调用 /oauth2/token 换 access_token
 * 
 * 本 Controller 仅提供注册、忘记密码、验证码等配套功能
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;



    @PostMapping("/forgot-password/verify")
    public ResponseEntity<Map<String, Object>> verifyForgotPassword(@Valid @RequestBody ForgotPasswordVerifyRequest request) {
        Map<String, Object> result = authService.verifyForgotPassword(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/send-verification-code")
    public ResponseEntity<Map<String, Object>> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        Map<String, Object> result = authService.sendVerificationCode(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/captcha")
    public ResponseEntity<Map<String, String>> getCaptcha() {
        Map<String, String> captcha = authService.getCaptcha();
        return ResponseEntity.ok(captcha);
    }

    @GetMapping("/check-captcha")
    public ResponseEntity<Boolean> checkShouldShowCaptcha(@RequestParam String username) {
        boolean shouldShow = authService.shouldShowCaptcha(username);
        return ResponseEntity.ok(shouldShow);
    }
}
