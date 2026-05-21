package com.sso.client;

import com.sso.dto.*;
import com.user.grpc.UserServiceGrpc;
import com.user.grpc.UserServiceProto;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserServiceClient {

    @GrpcClient("user-server")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public LoginResponse validateLogin(com.sso.dto.LoginRequest request) {
        UserServiceProto.LoginRequest grpcRequest = UserServiceProto.LoginRequest.newBuilder()
                .setUsername(request.getUsername())
                .setPassword(request.getPassword())
                .build();
        
        UserServiceProto.LoginResponse grpcResponse = userServiceStub.validateLogin(grpcRequest);
        
        LoginResponse response = new LoginResponse();
        response.setSuccess(grpcResponse.getSuccess());
        response.setMessage(grpcResponse.getMessage());
        response.setToken(grpcResponse.getToken());
        response.setUsername(grpcResponse.getUsername());
        return response;
    }

    public RegisterResponse register(com.sso.dto.RegisterRequest request) {
        UserServiceProto.RegisterRequest grpcRequest = UserServiceProto.RegisterRequest.newBuilder()
                .setUsername(request.getUsername())
                .setEmail(request.getEmail() != null ? request.getEmail() : "")
                .setPassword(request.getPassword())
                .setPhone(request.getPhone())
                .build();
        
        UserServiceProto.RegisterResponse grpcResponse = userServiceStub.register(grpcRequest);
        
        RegisterResponse response = new RegisterResponse();
        response.setSuccess(grpcResponse.getSuccess());
        response.setMessage(grpcResponse.getMessage());
        response.setUserId(grpcResponse.getUserId());
        return response;
    }

    public ForgotPasswordResponse forgotPassword(com.sso.dto.ForgotPasswordRequest request) {
        UserServiceProto.ForgotPasswordRequest grpcRequest = UserServiceProto.ForgotPasswordRequest.newBuilder()
                .setIdentifier(request.getUsername() != null ? request.getUsername() : "")
                .setNewPassword(request.getNewPassword())
                .build();
        
        UserServiceProto.ForgotPasswordResponse grpcResponse = userServiceStub.forgotPassword(grpcRequest);
        
        ForgotPasswordResponse response = new ForgotPasswordResponse();
        response.setSuccess(grpcResponse.getSuccess());
        response.setMessage(grpcResponse.getMessage());
        return response;
    }

    public Map<String, Object> findUserByIdentifier(String identifier) {
        Map<String, Object> result = new HashMap<>();
        
        UserServiceProto.FindUserRequest grpcRequest = UserServiceProto.FindUserRequest.newBuilder()
                .setIdentifier(identifier)
                .build();
        
        UserServiceProto.FindUserResponse grpcResponse = userServiceStub.findUserByIdentifier(grpcRequest);
        
        if (grpcResponse.getExists()) {
            result.put("success", true);
            result.put("username", grpcResponse.getUsername());
            result.put("email", grpcResponse.getEmail());
            result.put("phone", grpcResponse.getPhone());
            result.put("userId", grpcResponse.getUserId());
        } else {
            result.put("success", false);
            result.put("message", "用户不存在");
        }
        
        return result;
    }
}
