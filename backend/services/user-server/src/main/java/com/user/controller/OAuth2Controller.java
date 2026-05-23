package com.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/oauth2")
public class OAuth2Controller {

    @Value("${sso.oauth2.client-id}")
    private String clientId;

    @Value("${sso.oauth2.client-secret}")
    private String clientSecret;

    @Value("${sso.oauth2.token-uri}")
    private String tokenUri;

    @Value("${sso.oauth2.authorization-uri}")
    private String authorizationUri;

    @Value("${sso.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${sso.oauth2.scope}")
    private String scope;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> exchangeToken(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        
        if (code == null || code.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "授权码不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("code", code);
            params.add("redirect_uri", redirectUri);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUri, entity, Map.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response.getBody());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "令牌交换失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getOAuthConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("authorizationUri", authorizationUri);
        config.put("clientId", clientId);
        config.put("redirectUri", redirectUri);
        config.put("scope", scope);
        config.put("responseType", "code");
        return ResponseEntity.ok(config);
    }
}
