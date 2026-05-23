package com.sso.service;

import com.sso.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Map<String, Object> userInfo = userServiceClient.findUserByIdentifier(username);
        
        if (userInfo == null || !(Boolean) userInfo.getOrDefault("success", false)) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        String dbUsername = (String) userInfo.get("username");
        String dbPassword = (String) userInfo.get("password");

        return new User(
                dbUsername,
                dbPassword,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
