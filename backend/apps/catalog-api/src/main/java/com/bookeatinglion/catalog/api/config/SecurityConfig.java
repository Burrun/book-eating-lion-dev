package com.bookeatinglion.catalog.api.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // reading-progress는 /api/catalog/books/** 아래라 아래의 GET permitAll보다
                        // 먼저 매칭돼야 한다 — 순서가 바뀌면 GET이 인증 없이 뚫린다.
                        // (SecurityConfigTest.인증_없이_이어읽기_위치_조회는_401을_반환한다 가 이 순서를 지킨다)
                        .requestMatchers("/api/catalog/books/*/reading-progress")
                        .authenticated()
                        // 아래의 books GET permitAll보다 먼저 선언해야 EPUB URL이 공개되지 않는다.
                        .requestMatchers("/api/catalog/books/*/ebook")
                        .authenticated()
                        // 도서 조회는 비로그인도 가능하다.
                        .requestMatchers(HttpMethod.GET, "/api/catalog/books/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/categories/**")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        .requestMatchers("/api/catalog/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/catalog/wishlist/**", "/api/catalog/recent-books/**")
                        .authenticated()
                        .requestMatchers("/api/catalog/recommend/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/catalog/books/*/reviews")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/catalog/books/*/inquiries")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/catalog/inquiries/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/catalog/inquiries/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/catalog/books/*/restock-alert")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/catalog/books/*/restock-alert")
                        .authenticated()
                        .requestMatchers("/api/catalog/restock-alerts/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/catalog/reviews/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/catalog/reviews/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new HashSet<>();
            Collection<String> scopes = jwt.getClaimAsStringList("scope");
            if (scopes == null) {
                String scope = jwt.getClaimAsString("scope");
                scopes = scope == null || scope.isBlank() ? List.of() : List.of(scope.split(" "));
            }
            scopes.forEach(value -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + value)));

            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups != null) {
                groups.forEach(group -> authorities.add(new SimpleGrantedAuthority("ROLE_" + group)));
            }
            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            return authorities;
        });
        return converter;
    }
}
