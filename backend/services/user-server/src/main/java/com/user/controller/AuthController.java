package com.user.controller;

import com.user.dto.*;
import com.user.service.CaptchaService;
import com.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        ApiResponse<UserResponse> response = userService.register(request);
        return ResponseEntity.ok(new RegisterResponse(response.isSuccess(), response.getMessage()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ApiResponse<String> response = userService.forgotPassword(request);
        return ResponseEntity.ok(new ForgotPasswordResponse(response.isSuccess(), response.getMessage()));
    }

    @GetMapping("/captcha")
    public ResponseEntity<Map<String, String>> getCaptcha() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        return ResponseEntity.ok(captcha);
    }

    @GetMapping("/check-captcha")
    public ResponseEntity<Boolean> checkShouldShowCaptcha(@RequestParam String username) {
        boolean shouldShow = userService.shouldShowCaptcha(username);
        return ResponseEntity.ok(shouldShow);
    }

    @GetMapping("/find-user")
    public ResponseEntity<Map<String, Object>> findUserByIdentifier(@RequestParam String identifier) {
        Map<String, Object> result = userService.findUserByIdentifier(identifier);
        return ResponseEntity.ok(result);
    }
}
