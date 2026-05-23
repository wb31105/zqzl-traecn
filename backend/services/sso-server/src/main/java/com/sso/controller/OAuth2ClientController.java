package com.sso.controller;

import com.sso.entity.Client;
import com.sso.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 客户端管理接口（回调白名单管理）
 * 
 * 注意：Spring Security OAuth2 Authorization Server 已内置严格的 redirect_uri 校验：
 * 1. 必须与注册的完全匹配（不支持通配符）
 * 2. 不允许多重重定向
 * 3. 不支持相对路径
 * 
 * 安全机制：防止开放重定向攻击
 */
@RestController
@RequestMapping("/v1/oauth2/clients")
public class OAuth2ClientController {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<Client>> listClients() {
        return ResponseEntity.ok(clientRepository.findAll());
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<Client> getClient(@PathVariable String clientId) {
        Client client = clientRepository.findByClientId(clientId);
        if (client == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(client);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createClient(@Valid @RequestBody Client client) {
        Map<String, Object> result = new HashMap<>();
        
        if (clientRepository.findByClientId(client.getClientId()) != null) {
            result.put("success", false);
            result.put("message", "客户端ID已存在");
            return ResponseEntity.badRequest().body(result);
        }

        validateRedirectUri(client.getRedirectUri());
        
        client.setClientSecret(passwordEncoder.encode(client.getClientSecret()));
        Client saved = clientRepository.save(client);
        
        result.put("success", true);
        result.put("message", "客户端创建成功");
        result.put("data", saved);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateClient(
            @PathVariable Long id, 
            @Valid @RequestBody Client clientUpdate) {
        Map<String, Object> result = new HashMap<>();
        
        return clientRepository.findById(id)
                .map(client -> {
                    validateRedirectUri(clientUpdate.getRedirectUri());
                    
                    client.setClientName(clientUpdate.getClientName());
                    client.setRedirectUri(clientUpdate.getRedirectUri());
                    client.setScope(clientUpdate.getScope());
                    client.setRequireConsent(clientUpdate.isRequireConsent());
                    client.setEnabled(clientUpdate.isEnabled());
                    
                    if (clientUpdate.getClientSecret() != null && !clientUpdate.getClientSecret().isEmpty()) {
                        client.setClientSecret(passwordEncoder.encode(clientUpdate.getClientSecret()));
                    }
                    
                    Client saved = clientRepository.save(client);
                    result.put("success", true);
                    result.put("message", "客户端更新成功");
                    result.put("data", saved);
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteClient(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        if (!clientRepository.existsById(id)) {
            result.put("success", false);
            result.put("message", "客户端不存在");
            return ResponseEntity.notFound().build();
        }
        
        clientRepository.deleteById(id);
        result.put("success", true);
        result.put("message", "客户端删除成功");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/validate-redirect")
    public ResponseEntity<Map<String, Object>> validateRedirectUri(
            @RequestParam String clientId,
            @RequestParam String redirectUri) {
        Map<String, Object> result = new HashMap<>();
        
        Client client = clientRepository.findByClientId(clientId);
        if (client == null) {
            result.put("success", false);
            result.put("valid", false);
            result.put("message", "客户端不存在");
            return ResponseEntity.badRequest().body(result);
        }

        boolean isValid = client.getRedirectUri().equals(redirectUri);
        result.put("success", true);
        result.put("valid", isValid);
        result.put("registeredRedirectUri", client.getRedirectUri());
        result.put("message", isValid ? "回调地址有效" : "回调地址与注册地址不匹配");
        
        return ResponseEntity.ok(result);
    }

    private void validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            throw new IllegalArgumentException("回调地址不能为空");
        }
        if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
            throw new IllegalArgumentException("回调地址必须使用 HTTP 或 HTTPS 协议");
        }
        if (redirectUri.contains("#")) {
            throw new IllegalArgumentException("回调地址不能包含片段标识符 (#)");
        }
    }
}
