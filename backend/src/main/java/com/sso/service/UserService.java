package com.sso.service;

import com.sso.dto.LoginRequest;
import com.sso.dto.LoginResponse;
import com.sso.entity.User;
import com.sso.repository.UserRepository;
import com.sso.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CaptchaService captchaService;

    @Value("${sso.login.captcha-enabled-after}")
    private int captchaEnabledAfter;

    @PostConstruct
    public void initDefaultUser() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User user = new User();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setEmail("admin@sso.com");
            userRepository.save(user);
        }
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        
        if (user == null) {
            return new LoginResponse(false, "用户名或密码错误", null, false, null);
        }

        if (user.getLoginAttempts() >= captchaEnabledAfter) {
            if (request.getCaptcha() == null || request.getCaptchaKey() == null) {
                return new LoginResponse(false, "请输入验证码", null, true, user.getUsername());
            }
            
            if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
                return new LoginResponse(false, "验证码错误", null, true, user.getUsername());
            }
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            user.setLoginAttempts(user.getLoginAttempts() + 1);
            userRepository.save(user);
            
            boolean requireCaptcha = user.getLoginAttempts() >= captchaEnabledAfter;
            return new LoginResponse(false, "用户名或密码错误", null, requireCaptcha, user.getUsername());
        }

        user.setLoginAttempts(0);
        userRepository.save(user);

        if (request.getCaptchaKey() != null) {
            captchaService.removeCaptcha(request.getCaptchaKey());
        }

        String token = jwtUtil.generateToken(user.getUsername());
        return new LoginResponse(true, "登录成功", token, false, user.getUsername());
    }

    public boolean shouldShowCaptcha(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        return user != null && user.getLoginAttempts() >= captchaEnabledAfter;
    }
}
