package com.exelynt.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI with an "Authorize" button that sends {@code Authorization: Bearer <jwt>}. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI bookingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Resource Booking System API")
                        .version("1.0.0")
                        .description("""
                                Booking of rooms, vehicles and equipment.

                                Obtain a token pair from POST /auth/login, then click Authorize and paste \
                                the access token. Access tokens live for 15 minutes; rotate them with \
                                POST /auth/refresh.""")
                        .license(new License().name("Apache-2.0")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token issued by /auth/login")));
    }
}
