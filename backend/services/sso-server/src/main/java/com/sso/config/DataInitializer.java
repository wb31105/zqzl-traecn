package com.sso.config;

import com.sso.entity.Client;
import com.sso.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${sso.client.user-web-redirect-uri}")
    private String userWebRedirectUri;

    @Override
    public void run(String... args) {
        Client existingClient = clientRepository.findByClientId("user-web-client");
        if (existingClient == null) {
            Client userWebClient = new Client();
            userWebClient.setClientId("user-web-client");
            userWebClient.setClientSecret(passwordEncoder.encode("user-web-secret-123"));
            userWebClient.setClientName("用户管理系统");
            userWebClient.setRedirectUri(userWebRedirectUri);
            userWebClient.setScope("profile read write");
            userWebClient.setRequireConsent(false);
            userWebClient.setEnabled(true);
            clientRepository.save(userWebClient);
        } else {
            existingClient.setRedirectUri(userWebRedirectUri);
            existingClient.setScope("profile read write");
            clientRepository.save(existingClient);
        }
    }
}
