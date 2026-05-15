package com.sso.controller;

import com.sso.dto.*;
import com.sso.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
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

    @GetMapping("/validate-ticket")
    public ResponseEntity<String> validateTicket(@RequestParam String ticket) {
        String username = authService.validateTicket(ticket);
        if (username != null) {
            return ResponseEntity.ok(username);
        }
        return ResponseEntity.badRequest().body("无效的票据");
    }
}
