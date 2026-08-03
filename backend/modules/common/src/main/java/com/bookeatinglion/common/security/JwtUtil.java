package com.bookeatinglion.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT Access/Refresh 토큰의 발급과 검증을 전담하는 유틸리티 컴포넌트.
 *
 * <p>베타 프로젝트({@code book-eating-lion-beta})의 {@code JwtUtil}과 동일하게
 * Access Token과 Refresh Token에 서로 다른 서명 키를 사용한다. 두 토큰을 완전히 분리된
 * 키로 서명함으로써, 만에 하나 Access Token 서명 키가 유출되더라도 Refresh Token까지
 * 위조되는 것을 막을 수 있다.</p>
 *
 * <p>토큰의 {@code type} 클레임({@code "access"} 또는 {@code "refresh"})으로 두 토큰을
 * 구분하며, {@code /api/auth/refresh} 재발급 로직은 반드시 {@code type=refresh}인
 * 토큰만 허용해야 한다.</p>
 *
 * <p>시크릿 키와 만료 시간은 하드코딩하지 않고 {@code application-*.yml}의
 * {@code jwt.*} 프로퍼티(실제 값은 환경변수로 주입)에서 읽어온다.</p>
 */
@Component
public class JwtUtil {

    /** Access Token 서명에 사용할 Base64 인코딩된 비밀키 (환경변수 {@code JWT_SECRET_KEY}). */
    @Value("${jwt.secretKey}")
    private String secretKey;

    /** Refresh Token 서명에 사용할 Base64 인코딩된 비밀키 (환경변수 {@code JWT_SECRET_KEY_RT}). */
    @Value("${jwt.secretKeyRt}")
    private String secretKeyRt;

    /** Access Token 유효 기간(밀리초). 기본값 15분. */
    @Value("${jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    /** Refresh Token 유효 기간(밀리초). 기본값 14일. */
    @Value("${jwt.refresh-token-expiration-ms:1209600000}")
    private long refreshTokenExpirationMs;

    private SecretKey accessKey;
    private SecretKey refreshKey;

    /**
     * 빈 생성 이후 Base64로 인코딩되어 있는 문자열 프로퍼티를 실제 {@link SecretKey}로 변환한다.
     * 매 서명/검증마다 디코딩하지 않도록 애플리케이션 구동 시 한 번만 수행한다.
     */
    @PostConstruct
    public void init() {
        byte[] accessKeyBytes = Base64.getDecoder().decode(secretKey);
        byte[] refreshKeyBytes = Base64.getDecoder().decode(secretKeyRt);
        this.accessKey = Keys.hmacShaKeyFor(accessKeyBytes);
        this.refreshKey = Keys.hmacShaKeyFor(refreshKeyBytes);
    }

    /**
     * 로그인/토큰 재발급 시 사용할 Access Token을 발급한다.
     *
     * @param username 토큰의 subject로 사용할 회원 아이디
     * @param role     회원의 권한(예: "USER", "ADMIN"). 인가 필터에서 권한 판단에 사용된다.
     * @return 서명이 완료된 Access Token 문자열
     */
    public String createAccessToken(String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(accessKey)
                .compact();
    }

    /**
     * 로그인/토큰 재발급 시 사용할 Refresh Token을 발급한다.
     * Access Token보다 훨씬 긴 수명을 가지며, 오직 {@code /api/auth/refresh}에서만 사용되어야 한다.
     *
     * @param username 토큰의 subject로 사용할 회원 아이디
     * @param role     회원의 권한
     * @return 서명이 완료된 Refresh Token 문자열
     */
    public String createRefreshToken(String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(refreshKey)
                .compact();
    }

    /**
     * Access Token을 검증하고 클레임을 추출한다.
     *
     * @param token 검증할 Access Token
     * @return 토큰에 담긴 클레임(subject, role, type, 만료시각 등)
     * @throws io.jsonwebtoken.JwtException 서명이 유효하지 않거나 토큰이 만료/변조된 경우
     */
    public Claims parseAccessClaims(String token) {
        return Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Refresh Token을 검증하고 클레임을 추출한다.
     *
     * @param token 검증할 Refresh Token
     * @return 토큰에 담긴 클레임
     * @throws io.jsonwebtoken.JwtException 서명이 유효하지 않거나 토큰이 만료/변조된 경우
     */
    public Claims parseRefreshClaims(String token) {
        return Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
    }
}
