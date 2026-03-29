package ru.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import ru.security.InternalImportApiKeyAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public InternalImportApiKeyAuthenticationFilter internalImportApiKeyAuthenticationFilter(
            @org.springframework.beans.factory.annotation.Value("${internal.security.api-key}") String internalApiKey) {
        return new InternalImportApiKeyAuthenticationFilter(internalApiKey);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           InternalImportApiKeyAuthenticationFilter internalImportApiKeyAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalImportApiKeyAuthenticationFilter, BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/internal/import/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/groups/**", "/api/lessons/**", "/api/workload/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/groups/**", "/api/lessons/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PUT, "/api/groups/**", "/api/lessons/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/groups/**", "/api/lessons/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers("/api/groups/**", "/api/lessons/**", "/api/workload/**").authenticated()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
