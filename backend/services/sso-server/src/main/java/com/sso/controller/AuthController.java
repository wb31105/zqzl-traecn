package com.sso.controller;

import com.sso.dto.LoginRequest;
import com.sso.dto.LoginResponse;
import com.sso.service.CaptchaService;
import com.sso.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
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
}
