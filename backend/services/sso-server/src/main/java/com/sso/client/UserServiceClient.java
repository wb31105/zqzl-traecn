package com.sso.client;

import com.sso.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user.service.url:http://localhost:8081/user}")
    private String userServiceUrl;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public LoginResponse validateLogin(LoginRequest request) {
        String url = userServiceUrl + "/api/auth/login";
        return restTemplate.postForObject(url, request, LoginResponse.class);
    }

    public RegisterResponse register(RegisterRequest request) {
        String url = userServiceUrl + "/api/auth/register";
        return restTemplate.postForObject(url, request, RegisterResponse.class);
    }

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String url = userServiceUrl + "/api/auth/forgot-password";
        return restTemplate.postForObject(url, request, ForgotPasswordResponse.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> findUserByIdentifier(String identifier) {
        String url = userServiceUrl + "/api/auth/find-user?identifier=" + identifier;
        return restTemplate.getForObject(url, Map.class);
    }
}
