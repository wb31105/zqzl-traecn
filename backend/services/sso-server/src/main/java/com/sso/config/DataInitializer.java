package com.sso.config;

import com.sso.entity.Client;
import com.sso.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (clientRepository.findByClientId("user-web-client") == null) {
            Client userWebClient = new Client();
            userWebClient.setClientId("user-web-client");
            userWebClient.setClientSecret(passwordEncoder.encode("user-web-secret-123"));
            userWebClient.setClientName("用户管理系统");
            userWebClient.setRedirectUri("http://localhost:3001/sso/callback");
            userWebClient.setScope("openid,profile,read,write");
            userWebClient.setRequireConsent(false);
            userWebClient.setEnabled(true);
            clientRepository.save(userWebClient);
        }

        if (clientRepository.findByClientId("third-party-demo") == null) {
            Client thirdPartyClient = new Client();
            thirdPartyClient.setClientId("third-party-demo");
            thirdPartyClient.setClientSecret(passwordEncoder.encode("third-party-secret-456"));
            thirdPartyClient.setClientName("第三方演示应用");
            thirdPartyClient.setRedirectUri("http://localhost:3002/callback");
            thirdPartyClient.setScope("openid,profile");
            thirdPartyClient.setRequireConsent(true);
            thirdPartyClient.setEnabled(true);
            clientRepository.save(thirdPartyClient);
        }
    }
}
