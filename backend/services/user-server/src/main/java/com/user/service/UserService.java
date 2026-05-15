package com.user.service;

import com.user.dto.*;
import com.user.entity.User;
import com.user.repository.UserRepository;
import com.user.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;

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

    @Value("${user.login.captcha-enabled-after}")
    private int captchaEnabledAfter;

    @PostConstruct
    public void initDefaultUser() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User user = new User();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setEmail("admin@user.com");
            user.setNickname("管理员");
            user.setRole("ADMIN");
            userRepository.save(user);
        }
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        
        if (user == null) {
            return new LoginResponse(false, "用户名或密码错误", null, false, null);
        }

        if (!user.getEnabled()) {
            return new LoginResponse(false, "账号已被禁用", null, false, null);
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
        user.setLastLoginTime(LocalDateTime.now());
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

    public ApiResponse<UserResponse> register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return ApiResponse.error("两次输入的密码不一致");
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ApiResponse.error("用户名已存在");
        }

        if (StringUtils.hasText(request.getEmail()) && userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ApiResponse.error("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole("USER");
        user.setEnabled(true);

        User savedUser = userRepository.save(user);
        return ApiResponse.success("注册成功", convertToResponse(savedUser));
    }

    public ApiResponse<String> forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }

        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equals(user.getEmail())) {
            return ApiResponse.error("邮箱不匹配");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ApiResponse.error("两次输入的密码不一致");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setLoginAttempts(0);
        userRepository.save(user);

        return ApiResponse.success("密码重置成功", null);
    }

    public Page<UserResponse> getAllUsers(String keyword, Pageable pageable) {
        Page<User> users;
        if (StringUtils.hasText(keyword)) {
            users = userRepository.findByUsernameContainingOrEmailContainingOrNicknameContaining(
                keyword, keyword, keyword, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(this::convertToResponse);
    }

    public Optional<UserResponse> getUserById(Long id) {
        return userRepository.findById(id).map(this::convertToResponse);
    }

    public Optional<UserResponse> getUserByUsername(String username) {
        return userRepository.findByUsername(username).map(this::convertToResponse);
    }

    public ApiResponse<UserResponse> updateUser(Long id, UpdateUserRequest request) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ApiResponse.error("用户不存在");
        }

        User user = userOpt.get();
        
        if (StringUtils.hasText(request.getEmail())) {
            Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
            if (existingUser.isPresent() && !existingUser.get().getId().equals(id)) {
                return ApiResponse.error("邮箱已被其他用户使用");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (StringUtils.hasText(request.getRole())) {
            user.setRole(request.getRole());
        }

        User savedUser = userRepository.save(user);
        return ApiResponse.success("更新成功", convertToResponse(savedUser));
    }

    public ApiResponse<String> resetPassword(Long id, ResetPasswordRequest request) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ApiResponse.error("用户不存在");
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setLoginAttempts(0);
        userRepository.save(user);

        return ApiResponse.success("密码重置成功", null);
    }

    public ApiResponse<String> toggleUserStatus(Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ApiResponse.error("用户不存在");
        }

        User user = userOpt.get();
        user.setEnabled(!user.getEnabled());
        userRepository.save(user);

        String status = user.getEnabled() ? "启用" : "禁用";
        return ApiResponse.success("用户已" + status, null);
    }

    public ApiResponse<String> deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            return ApiResponse.error("用户不存在");
        }
        userRepository.deleteById(id);
        return ApiResponse.success("删除成功", null);
    }

    private UserResponse convertToResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPhone(),
            user.getNickname(),
            user.getAvatar(),
            user.getRole(),
            user.getEnabled(),
            user.getLoginAttempts(),
            user.getLastLoginTime(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
