package com.sso.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class VerificationCodeService {

    private final Map<String, CodeEntry> codeStorage = new ConcurrentHashMap<>();
    private static final long CODE_EXPIRATION_MINUTES = 5;
    private static final int CODE_LENGTH = 6;

    private static class CodeEntry {
        String code;
        long timestamp;

        CodeEntry(String code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
        }
    }

    public String generateAndSendCode(String identifier) {
        String code = generateRandomCode();
        long timestamp = System.currentTimeMillis();
        codeStorage.put(identifier, new CodeEntry(code, timestamp));
        
        System.out.println("========================================");
        System.out.println("验证码已发送至: " + identifier);
        System.out.println("验证码: " + code);
        System.out.println("有效期: " + CODE_EXPIRATION_MINUTES + " 分钟");
        System.out.println("========================================");
        
        return code;
    }

    public boolean verifyCode(String identifier, String inputCode) {
        CodeEntry entry = codeStorage.get(identifier);
        if (entry == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        long age = now - entry.timestamp;
        
        if (age > TimeUnit.MINUTES.toMillis(CODE_EXPIRATION_MINUTES)) {
            codeStorage.remove(identifier);
            return false;
        }
        
        if (entry.code.equals(inputCode)) {
            codeStorage.remove(identifier);
            return true;
        }
        
        return false;
    }

    private String generateRandomCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    public void removeCode(String identifier) {
        codeStorage.remove(identifier);
    }
}
