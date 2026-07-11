package com.MSyamsandiYW.order_service.config;

import com.MSyamsandiYW.common.jwt.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtServiceConfig {

    @Bean
    public JwtService jwtService(@Value("${jwt.public-key}") String jwtPublicKey) {
        return new JwtService(jwtPublicKey);
    }
}
