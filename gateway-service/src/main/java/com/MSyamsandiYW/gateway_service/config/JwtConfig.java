package com.MSyamsandiYW.gateway_service.config;

import com.MSyamsandiYW.common.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtService jwtService() {
        return new JwtService();
    }
}
