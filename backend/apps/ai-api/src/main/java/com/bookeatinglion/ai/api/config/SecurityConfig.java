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
                        // 🔴 /ask 가 이 아래에 있다. 먹인 책 본문을 읽는 경로라 인증이 없으면
                        // "내가 먹인 책"이 정의되지 않고, 그 순간 검색 필터가 비어 접근 제어가
                        // 통째로 사라진다. 이 줄을 좁히거나 지우면 안 된다.
                        .requestMatchers("/api/ai/lion/**")
                        .authenticated()
                        // 티켓 발급은 여기서 인증한다. WebSocket 핸드셰이크(/ws/**)는 헤더를
                        // 붙일 수 없어 이 체인으로 막을 수 없고, ChatHandshakeInterceptor 가
                        // 티켓으로 검사한다.
                        .requestMatchers("/api/ai/bot/chat/ticket")
                        .authenticated()
                        .requestMatchers("/ws/**")
                        .permitAll()
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
