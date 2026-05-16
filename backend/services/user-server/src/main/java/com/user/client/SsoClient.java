package com.user.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class SsoClient {

    private final RestTemplate restTemplate;

    @Value("${sso.service.url:http://localhost:8080/sso}")
    private String ssoServiceUrl;

    public SsoClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String validateTicket(String ticket) {
        try {
            String url = ssoServiceUrl + "/api/auth/validate-ticket?ticket=" + ticket;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return (String) response.get("username");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
