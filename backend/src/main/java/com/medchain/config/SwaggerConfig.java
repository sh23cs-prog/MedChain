package com.medchain.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 3.0 Configuration
 *
 * Accessible at: http://localhost:8080/swagger-ui.html
 * JSON:          http://localhost:8080/api-docs
 *
 * All endpoints require JWT Bearer token authentication.
 * Use POST /api/auth/login to get a token, then paste it in the "Authorize" dialog.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI medChainOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MedChain API")
                        .description("""
                            ## Blockchain-Aided Privacy-Preserving Medical Data Sharing
                            
                            Based on the IEEE paper: *"Blockchain-Aided Privacy-Preserving Medical Data Sharing 
                            Scheme for E-Healthcare System"*
                            
                            ### Security Stack
                            - **CP-ABE** — Ciphertext-Policy Attribute-Based Encryption
                            - **AES-256-GCM** — File-level authenticated encryption  
                            - **PRE** — Proxy Re-Encryption for secure key delegation
                            - **Pseudonyms** — SHA3-256(identity ∥ nonce) — real identity never on-chain
                            - **Isolation Forest** — ML-based anomaly detection on access patterns
                            - **JWT** — Stateless authentication with RBAC
                            
                            ### Quick Start
                            1. Register a user via `POST /api/auth/register`
                            2. Login via `POST /api/auth/login` to get JWT token
                            3. Click **Authorize** above and paste: `Bearer <your-token>`
                            4. Use the record, admin, or metrics endpoints
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MedChain Team")
                                .email("team@medchain.io")
                                .url("https://github.com/medchain"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("http://backend:8080").description("Docker Compose")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token from POST /api/auth/login")));
    }
}
