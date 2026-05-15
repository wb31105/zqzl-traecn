package com.sso.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class CaptchaService {

    private final Map<String, String> captchaStore = new HashMap<>();
    private final Random random = new Random();

    public Map<String, String> generateCaptcha() {
        String captchaKey = UUID.randomUUID().toString();
        String captchaCode = generateRandomCode(4);
        
        captchaStore.put(captchaKey, captchaCode);
        
        String base64Image = generateCaptchaImage(captchaCode);
        
        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("captchaImage", base64Image);
        
        return result;
    }

    public boolean validateCaptcha(String captchaKey, String captchaCode) {
        String storedCode = captchaStore.get(captchaKey);
        if (storedCode != null && storedCode.equalsIgnoreCase(captchaCode)) {
            captchaStore.remove(captchaKey);
            return true;
        }
        return false;
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateCaptchaImage(String code) {
        int width = 120;
        int height = 40;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 28));
        
        for (int i = 0; i < code.length(); i++) {
            g2d.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180)));
            g2d.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }
        
        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g2d.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
        }
        
        g2d.dispose();
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }
    }

    public void removeCaptcha(String captchaKey) {
        captchaStore.remove(captchaKey);
    }
}
