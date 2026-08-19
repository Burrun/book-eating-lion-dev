package com.bookeatinglion.order.api.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
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
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        // /internal/** 은 클러스터 내부 전용이다. JWT 를 요구하지 않는 대신
                        // Ingress 가 외부 노출을 막고 NetworkPolicy 가 출처를 제한한다.
                        // 애플리케이션 레벨에서 또 막으면 catalog-service 가 호출할 수 없다.
                        .requestMatchers("/internal/**")
                        .permitAll()
                        // /api/coupons/** → authenticated() 보다 먼저 매칭돼야 한다 — 순서가
                        // 바뀌면 넓은 규칙이 먼저 걸려 일반 회원도 admin 쿠폰 API를 호출할 수 있다.
                        .requestMatchers("/api/coupons/admin/**")
                        .hasRole("ADMIN")
                        // /api/orders/admin/** → authenticated() 보다 먼저 매칭돼야 한다 — 위 coupon
                        // 규칙과 같은 이유다 (넓은 규칙이 먼저 걸리면 일반 회원도 호출할 수 있다).
                        .requestMatchers("/api/orders/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/orders/**")
                        .authenticated()
                        .requestMatchers("/api/cart/**")
                        .authenticated()
                        .requestMatchers("/api/coupons/**")
                        .authenticated()
                        .requestMatchers("/api/payments/**")
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
