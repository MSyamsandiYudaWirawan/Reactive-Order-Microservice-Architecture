package com.MSyamsandiYW.inventory_service.config;

import com.MSyamsandiYW.common.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtServiceConfig {

    @Bean
    public JwtService jwtService() {
        return new JwtService();
    }
}
