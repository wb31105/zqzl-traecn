package com.sso.config;

import com.sso.entity.Client;
import com.sso.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.time.Duration;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    @Value("${sso.oauth2.issuer-uri}")
    private String issuerUri;

    @Value("${sso.web.login-url}")
    private String ssoLoginUrl;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(ssoLoginUrl))
        );
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(ClientRepository clientRepository) {
        return new RegisteredClientRepository() {
            @Override
            public RegisteredClient findByClientId(String clientId) {
                Client client = clientRepository.findByClientId(clientId);
                if (client == null) {
                    return null;
                }
                RegisteredClient.Builder builder = RegisteredClient.withId(client.getId().toString())
                        .clientId(client.getClientId())
                        .clientSecret(client.getClientSecret())
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .redirectUri(client.getRedirectUri());

                String scopeStr = client.getScope();
                if (scopeStr != null && !scopeStr.isEmpty()) {
                    for (String scope : scopeStr.split("[,\\s]+")) {
                        if (!scope.isEmpty()) {
                            builder.scope(scope);
                        }
                    }
                }

                return builder
                        .clientSettings(ClientSettings.builder()
                                .requireAuthorizationConsent(client.isRequireConsent())
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofHours(2))
                                .refreshTokenTimeToLive(Duration.ofDays(30))
                                .reuseRefreshTokens(false)
                                .build())
                        .build();
            }

            @Override
            public RegisteredClient findById(String id) {
                return clientRepository.findById(Long.parseLong(id))
                        .map(client -> findByClientId(client.getClientId()))
                        .orElse(null);
            }

            @Override
            public void save(RegisteredClient registeredClient) {
                Client client = new Client();
                client.setClientId(registeredClient.getClientId());
                client.setClientSecret(registeredClient.getClientSecret());
                client.setClientName(registeredClient.getClientId());
                client.setRedirectUri(registeredClient.getRedirectUris().iterator().next());
                client.setScope(String.join(" ", registeredClient.getScopes()));
                client.setRequireConsent(registeredClient.getClientSettings().isRequireAuthorizationConsent());
                client.setEnabled(true);
                clientRepository.save(client);
            }
        };
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .authorizationEndpoint("/oauth2/authorize")
                .tokenEndpoint("/oauth2/token")
                .tokenIntrospectionEndpoint("/oauth2/introspect")
                .tokenRevocationEndpoint("/oauth2/revoke")
                .jwkSetEndpoint("/oauth2/jwks")
                .oidcUserInfoEndpoint("/userinfo")
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
