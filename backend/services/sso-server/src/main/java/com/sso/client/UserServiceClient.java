package com.sso.client;

import com.sso.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "user-server", path = "")
public interface UserServiceClient {

    @PostMapping("/v1/auth/login")
    LoginResponse validateLogin(@RequestBody LoginRequest request);

    @PostMapping("/v1/auth/register")
    RegisterResponse register(@RequestBody RegisterRequest request);

    @PostMapping("/v1/auth/forgot-password")
    ForgotPasswordResponse forgotPassword(@RequestBody ForgotPasswordRequest request);

    @GetMapping("/v1/auth/find-user")
    Map<String, Object> findUserByIdentifier(@RequestParam("identifier") String identifier);
}
