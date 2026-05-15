package com.user.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
