package com.sso.client;

import com.sso.dto.*;
import com.user.grpc.UserServiceGrpc;
import com.user.grpc.UserServiceProto;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);

    @GrpcClient("user-server")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public LoginResponse validateLogin(com.sso.dto.LoginRequest request) {
        try {
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
        } catch (StatusRuntimeException e) {
            logger.error("gRPC调用失败 - validateLogin, username: {}, error: {}", request.getUsername(), e.getMessage(), e);
            LoginResponse response = new LoginResponse();
            response.setSuccess(false);
            response.setMessage("认证服务暂时不可用，请稍后重试");
            return response;
        } catch (Exception e) {
            logger.error("调用用户服务验证登录失败, username: {}, error: {}", request.getUsername(), e.getMessage(), e);
            LoginResponse response = new LoginResponse();
            response.setSuccess(false);
            response.setMessage("登录失败，请稍后重试");
            return response;
        }
    }

    public RegisterResponse register(com.sso.dto.RegisterRequest request) {
        try {
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
        } catch (StatusRuntimeException e) {
            logger.error("gRPC调用失败 - register, username: {}, error: {}", request.getUsername(), e.getMessage(), e);
            RegisterResponse response = new RegisterResponse();
            response.setSuccess(false);
            response.setMessage("注册服务暂时不可用，请稍后重试");
            return response;
        } catch (Exception e) {
            logger.error("调用用户服务注册失败, username: {}, error: {}", request.getUsername(), e.getMessage(), e);
            RegisterResponse response = new RegisterResponse();
            response.setSuccess(false);
            response.setMessage("注册失败，请稍后重试");
            return response;
        }
    }

    public ForgotPasswordResponse forgotPassword(com.sso.dto.ForgotPasswordRequest request) {
        try {
            UserServiceProto.ForgotPasswordRequest grpcRequest = UserServiceProto.ForgotPasswordRequest.newBuilder()
                    .setIdentifier(request.getUsername() != null ? request.getUsername() : "")
                    .setNewPassword(request.getNewPassword())
                    .build();
            
            UserServiceProto.ForgotPasswordResponse grpcResponse = userServiceStub.forgotPassword(grpcRequest);
            
            ForgotPasswordResponse response = new ForgotPasswordResponse();
            response.setSuccess(grpcResponse.getSuccess());
            response.setMessage(grpcResponse.getMessage());
            return response;
        } catch (StatusRuntimeException e) {
            logger.error("gRPC调用失败 - forgotPassword, identifier: {}, error: {}", request.getUsername(), e.getMessage(), e);
            ForgotPasswordResponse response = new ForgotPasswordResponse();
            response.setSuccess(false);
            response.setMessage("密码重置服务暂时不可用，请稍后重试");
            return response;
        } catch (Exception e) {
            logger.error("调用用户服务重置密码失败, identifier: {}, error: {}", request.getUsername(), e.getMessage(), e);
            ForgotPasswordResponse response = new ForgotPasswordResponse();
            response.setSuccess(false);
            response.setMessage("密码重置失败，请稍后重试");
            return response;
        }
    }

    public Map<String, Object> findUserByIdentifier(String identifier) {
        Map<String, Object> result = new HashMap<>();
        
        try {
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
        } catch (StatusRuntimeException e) {
            logger.error("gRPC调用失败 - findUserByIdentifier, identifier: {}, error: {}", identifier, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询服务暂时不可用，请稍后重试");
            return result;
        } catch (Exception e) {
            logger.error("调用用户服务查询用户失败, identifier: {}, error: {}", identifier, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询失败，请稍后重试");
            return result;
        }
    }
}
