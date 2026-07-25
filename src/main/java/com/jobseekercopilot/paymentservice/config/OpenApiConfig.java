package com.jobseekercopilot.paymentservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "serviceToken",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Service-Token")
                                .description("Dedicated service identity; caller permissions are route-scoped.")))
                .info(new Info()
                        .title("Payment Service API")
                        .description("Owns AI Credit wallets, transactions and reservations. Every payment API "
                                + "operation requires an authorized service identity; owner-scoped operations "
                                + "also require the trusted X-Payment-Owner context.")
                        .version("2.0.0")
                        .contact(new Contact().name("Jobseeker Copilot"))
                        .license(new License().name("MIT")));
    }
}
