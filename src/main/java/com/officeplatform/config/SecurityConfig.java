package com.officeplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.officeplatform.security.api.ApiKeyFilter;
import com.officeplatform.security.widget.WidgetTokenFilter;

@Configuration
public class SecurityConfig {

    private final ApiKeyFilter        apiKeyFilter;
    private final WidgetTokenFilter   widgetTokenFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(ApiKeyFilter apiKeyFilter,
                          WidgetTokenFilter widgetTokenFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.apiKeyFilter           = apiKeyFilter;
        this.widgetTokenFilter      = widgetTokenFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/admin/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Anonymous auth defaults to on in Spring Security; with it on, a request with no/bad
            // credentials is "authenticated as anonymous" and merely lacks ROLE_ADMIN, which the
            // ExceptionTranslationFilter reports as 403 (AccessDenied) instead of 401. Disabling it
            // makes such a request genuinely unauthenticated, so it hits the Basic auth entry point
            // (401 + WWW-Authenticate) as it should for both missing and wrong credentials.
            .anonymous(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().hasRole("ADMIN"))
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/health",
                        "/api/auth/resolve",          // <-- endpoint público para Widget Token
                        "/error",
                        // springdoc.swagger-ui.path is /swagger-ui.html, and that exact path is not
                        // covered by /swagger-ui/** — without it the configured entry point answers
                        // 403 and only the redirect target is reachable.
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/api-docs/**",
                        "/api/onlyoffice/callback",
                        "/api/files/download/**",
                        "/api/editor/proxy/**",
                        "/office-platform-widget.js",
                        "/test.html",
                        "/admin/**").permitAll()
                .anyRequest().authenticated())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            // WidgetTokenFilter primero: procesa Bearer tokens.
            // ApiKeyFilter segundo: procesa X-Api-Key (flujo clásico).
            // Ambos pueden coexistir; si el request no tiene Bearer, el
            // WidgetTokenFilter lo deja pasar intacto al ApiKeyFilter.
            .addFilterBefore(widgetTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyFilter, WidgetTokenFilter.class);

        return http.build();
    }

}

