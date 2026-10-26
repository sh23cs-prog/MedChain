package com.medchain.config;

import com.medchain.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security Configuration
 *
 * Authentication: JWT Bearer tokens (stateless)
 * Authorization:  Role-based access control (PATIENT, DOCTOR, ADMIN)
 * Passwords:      BCrypt (strength 10)
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private com.medchain.security.CustomAuthenticationEntryPoint authEntryPoint;

    @Autowired
    private com.medchain.security.CustomAccessDeniedHandler accessDeniedHandler;

    @Value("${cors.allowed.origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // ── Allow all pre-flight OPTIONS requests ─────────────────────
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Public endpoints ──────────────────────────────────────────
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // ── Admin only ────────────────────────────────────────────────
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/reports/**").hasRole("ADMIN")

                // ── Doctor-specific ───────────────────────────────────────────
                .requestMatchers("/api/records/doctor/**").hasAnyRole("DOCTOR", "ADMIN")

                // ── Record View / Download (Patient owner, authorized Doctor, Admin) ──
                .requestMatchers("/api/records/*/view").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                .requestMatchers("/api/records/*/download").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                .requestMatchers("/api/records/*/file").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                // ── Patient-specific ──────────────────────────────────────────
                .requestMatchers("/api/records/upload").hasAnyRole("PATIENT", "ADMIN")
                .requestMatchers("/api/records/upload-file").hasAnyRole("PATIENT", "ADMIN")

                // ── Authenticated (any role) ──────────────────────────────────
                .requestMatchers("/api/records/**").authenticated()
                .requestMatchers("/api/metrics/**").authenticated()
                .requestMatchers("/api/blockchain/**").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> originPatterns = new ArrayList<>();
        originPatterns.add("https://med-chain-amber.vercel.app");
        originPatterns.add("https://*.vercel.app");
        originPatterns.add("https://*.onrender.com");
        originPatterns.add("http://localhost:[*]");
        originPatterns.add("http://127.0.0.1:[*]");

        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            for (String origin : allowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.equals("*")) {
                        originPatterns.clear();
                        originPatterns.add("*");
                        break;
                    } else {
                        originPatterns.add(trimmed);
                    }
                }
            }
        }

        config.setAllowedOriginPatterns(originPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
