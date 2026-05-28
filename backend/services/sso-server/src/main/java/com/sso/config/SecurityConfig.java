package com.sso.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GrpcAuthenticationProvider grpcAuthenticationProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${sso.web.login-url}")
    private String ssoLoginUrl;

    @Value("${sso.web.logout-success-url}")
    private String logoutSuccessUrl;

    public SecurityConfig(GrpcAuthenticationProvider grpcAuthenticationProvider) {
        this.grpcAuthenticationProvider = grpcAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, RequestCache requestCache) throws Exception {
        http
            .authenticationProvider(grpcAuthenticationProvider)
            .cors().and()
            .csrf().disable()
            .requestCache()
                .requestCache(requestCache)
                .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
            .authorizeRequests()
                .antMatchers("/v1/auth/**", "/oauth2/**", "/.well-known/**", "/userinfo").permitAll()
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .loginPage(ssoLoginUrl)
                .loginProcessingUrl("/v1/auth/login")
                .successHandler(jsonAuthenticationSuccessHandler(requestCache))
                .failureHandler(jsonAuthenticationFailureHandler())
                .permitAll()
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl(logoutSuccessUrl)
                .invalidateHttpSession(true)
                .deleteCookies("SSO_SESSION")
                .permitAll();

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler jsonAuthenticationSuccessHandler(RequestCache requestCache) {
        return (request, response, authentication) -> {
            SavedRequest savedRequest = requestCache.getRequest(request, response);
            String redirectUrl = "/";
            if (savedRequest != null) {
                redirectUrl = savedRequest.getRedirectUrl();
            }

            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("redirectUrl", redirectUrl);
            response.getWriter().write(objectMapper.writeValueAsString(result));
        };
    }

    @Bean
    public SimpleUrlAuthenticationFailureHandler jsonAuthenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(javax.servlet.http.HttpServletRequest request,
                                                javax.servlet.http.HttpServletResponse response,
                                                org.springframework.security.core.AuthenticationException exception) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", exception.getMessage() != null ? exception.getMessage() : "用户名或密码错误");
                try {
                    response.getWriter().write(objectMapper.writeValueAsString(result));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
