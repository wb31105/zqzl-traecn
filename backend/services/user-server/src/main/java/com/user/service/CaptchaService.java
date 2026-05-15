package com.user.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    
    private final ConcurrentHashMap<String, String> captchaStore = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public Map<String, String> generateCaptcha() {
        String captchaKey = UUID.randomUUID().toString();
        String captchaCode = generateRandomCode();
        
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 28));
        
        for (int i = 0; i < CODE_LENGTH; i++) {
            g.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            g.drawString(String.valueOf(captchaCode.charAt(i)), 25 + i * 22, 30);
        }
        
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        
        g.dispose();
        
        String base64Image = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        captchaStore.put(captchaKey, captchaCode);
        
        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("captchaImage", base64Image);
        
        return result;
    }

    public boolean validateCaptcha(String captchaKey, String captcha) {
        String storedCode = captchaStore.get(captchaKey);
        if (storedCode == null) {
            return false;
        }
        boolean valid = storedCode.equalsIgnoreCase(captcha);
        if (valid) {
            captchaStore.remove(captchaKey);
        }
        return valid;
    }

    public void removeCaptcha(String captchaKey) {
        captchaStore.remove(captchaKey);
    }

    private String generateRandomCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return code.toString();
    }
}
