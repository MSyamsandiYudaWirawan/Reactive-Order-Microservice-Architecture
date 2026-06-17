package com.MSyamsandiYW.auth_service.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
        info = @Info(
                contact = @Contact(
                        name = "auth-service",
                        email = "zxvcklh@gmail.com",
                        url = "https://github.com/MSyamsandiYudaWirawan"
                ),
                description = "Openapi documentation auth-service",
                title = "OpenApi Specification",
                version = "1.0",
                license = @License(
                        name = "MIT License",
                        url = "https://github.com/MSyamsandiYudaWirawan/Reactive-Order-Microservice-Architecture/blob/master/LICENSE"
                ),
                termsOfService = "https://github.com/MSyamsandiYudaWirawan/Reactive-Order-Microservice-Architecture/blob/master/TERMS"
        ),
        security = {
                @SecurityRequirement(
                        name = "bearerAuth"
                )
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT auth description",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
