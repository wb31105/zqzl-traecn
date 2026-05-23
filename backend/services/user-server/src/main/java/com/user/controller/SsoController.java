package com.user.controller;

import com.user.client.SsoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/sso")
@CrossOrigin(originPatterns = "*")
public class SsoController {

    @Autowired
    private SsoClient ssoClient;

    @GetMapping("/validate-ticket")
    public ResponseEntity<Map<String, Object>> validateTicket(@RequestParam String ticket) {
        String username = ssoClient.validateTicket(ticket);
        
        Map<String, Object> result = new HashMap<>();
        if (username != null) {
            result.put("success", true);
            result.put("username", username);
            result.put("message", "验证成功");
        } else {
            result.put("success", false);
            result.put("message", "票据无效或已过期");
        }
        
        return ResponseEntity.ok(result);
    }
}
