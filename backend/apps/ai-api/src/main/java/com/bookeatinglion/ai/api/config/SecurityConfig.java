package com.bookeatinglion.ai.api.config;

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
<<<<<<< HEAD
                        // 라이언은 프리미엄 전용이다.
                        .requestMatchers("/api/ai/lions/**")
=======
                        // 먹인 책 본문을 읽는 경로다. 인증이 없으면 "내가 먹인 책"이 정의되지 않고,
                        // 그 순간 검색 필터가 비어 접근 제어가 통째로 사라진다.
                        .requestMatchers("/api/ai/ask")
                        .authenticated()
                        .requestMatchers("/api/ai/lion/**")
>>>>>>> origin/feature/BOO-27-rag
                        .authenticated()
                        .requestMatchers("/api/ai/bot/inquiries")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
