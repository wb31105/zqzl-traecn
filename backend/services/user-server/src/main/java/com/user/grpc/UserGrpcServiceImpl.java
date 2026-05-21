package com.user.grpc;

import com.user.entity.User;
import com.user.repository.UserRepository;
import com.user.util.JwtUtil;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@GrpcService
public class UserGrpcServiceImpl extends UserServiceGrpc.UserServiceImplBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void validateLogin(UserServiceProto.LoginRequest request, StreamObserver<UserServiceProto.LoginResponse> responseObserver) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        
        UserServiceProto.LoginResponse.Builder response = UserServiceProto.LoginResponse.newBuilder();
        
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            response.setSuccess(false).setMessage("用户名或密码错误");
        } else if (!user.getEnabled()) {
            response.setSuccess(false).setMessage("账号已被禁用");
        } else {
            user.setLoginAttempts(0);
            userRepository.save(user);
            String token = jwtUtil.generateToken(user.getUsername());
            response.setSuccess(true)
                    .setMessage("登录成功")
                    .setToken(token)
                    .setUsername(user.getUsername());
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void register(UserServiceProto.RegisterRequest request, StreamObserver<UserServiceProto.RegisterResponse> responseObserver) {
        UserServiceProto.RegisterResponse.Builder response = UserServiceProto.RegisterResponse.newBuilder();
        
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            response.setSuccess(false).setMessage("用户名已存在");
        } else if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            response.setSuccess(false).setMessage("手机号已被注册");
        } else {
            User user = new User();
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setPhone(request.getPhone());
            user.setEmail(request.getEmail());
            user.setNickname(request.getUsername());
            user.setRole("USER");
            user.setEnabled(true);
            
            User savedUser = userRepository.save(user);
            response.setSuccess(true)
                    .setMessage("注册成功")
                    .setUserId(savedUser.getId().toString());
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void forgotPassword(UserServiceProto.ForgotPasswordRequest request, StreamObserver<UserServiceProto.ForgotPasswordResponse> responseObserver) {
        UserServiceProto.ForgotPasswordResponse.Builder response = UserServiceProto.ForgotPasswordResponse.newBuilder();
        
        User user = findUserByIdentifierInternal(request.getIdentifier());
        if (user == null) {
            response.setSuccess(false).setMessage("用户不存在");
        } else {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            user.setLoginAttempts(0);
            userRepository.save(user);
            response.setSuccess(true).setMessage("密码重置成功");
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void findUserByIdentifier(UserServiceProto.FindUserRequest request, StreamObserver<UserServiceProto.FindUserResponse> responseObserver) {
        UserServiceProto.FindUserResponse.Builder response = UserServiceProto.FindUserResponse.newBuilder();
        
        User user = findUserByIdentifierInternal(request.getIdentifier());
        if (user != null) {
            response.setExists(true)
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail() != null ? user.getEmail() : "")
                    .setPhone(user.getPhone() != null ? user.getPhone() : "")
                    .setUserId(user.getId().toString());
        } else {
            response.setExists(false);
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private User findUserByIdentifierInternal(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmail(identifier).orElse(null);
        } else if (identifier.matches("^1[3-9]\\d{9}$")) {
            return userRepository.findByPhone(identifier).orElse(null);
        } else {
            return userRepository.findByUsername(identifier).orElse(null);
        }
    }
}
