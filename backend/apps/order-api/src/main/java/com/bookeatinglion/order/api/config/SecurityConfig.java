package com.bookeatinglion.order.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
                        .requestMatchers("/api/orders/**")
                        .authenticated()
                        .requestMatchers("/api/cart/**")
                        .authenticated()
                        .requestMatchers("/api/coupons/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
