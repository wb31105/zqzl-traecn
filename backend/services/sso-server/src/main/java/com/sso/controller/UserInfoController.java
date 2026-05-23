package com.sso.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping
public class UserInfoController {

    @Value("${sso.oauth2.issuer-uri}")
    private String issuerUri;

    @GetMapping("/userinfo")
    public Map<String, Object> userInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("sub", authentication.getName());
        userInfo.put("username", authentication.getName());
        userInfo.put("email", authentication.getName() + "@example.com");
        
        return userInfo;
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> openidConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("issuer", issuerUri);
        config.put("authorization_endpoint", issuerUri + "/oauth2/authorize");
        config.put("token_endpoint", issuerUri + "/oauth2/token");
        config.put("userinfo_endpoint", issuerUri + "/userinfo");
        config.put("jwks_uri", issuerUri + "/oauth2/jwks");
        config.put("response_types_supported", new String[]{"code", "token", "id_token"});
        config.put("grant_types_supported", new String[]{"authorization_code", "refresh_token", "client_credentials"});
        config.put("subject_types_supported", new String[]{"public"});
        config.put("id_token_signing_alg_values_supported", new String[]{"RS256"});
        return config;
    }
}
