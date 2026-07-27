package com.ott.common.security.jwt;

import com.ott.common.core.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;


/**
 * Access/Refresh JWT 생성
 * JWT 검증, JWT 파싱(claim 추출)
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_AUTH = "auth";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey key;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;


    public JwtTokenProvider(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.access-token-expiry}")long accessTokenExpiry,
            @Value("${jwt.refresh-token-expiry}")long refreshTokenExpiry) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    // Access JWT 생성
    public String createAccessToken(Long memberId, List<String> authorities) {
        return createToken(memberId, authorities, accessTokenExpiry, "access");
    }

    // Refresh JWT 생성
    public String createRefreshToken(Long memberId, List<String> authorities) {
        return createToken(memberId, authorities, refreshTokenExpiry, "refresh");
    }

    // JWT 생성
    // header는 자동으로 생김
    // claim -> sub, auth, iat(issued at), exp(expiration)이 들어감
    // claim -> 추가로 type을 넣어서 access, refresh 토큰 구별
    // signature -> Ec(header+payload)
    private String createToken(Long memberId, List<String> authorities, long expiryMillis, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_AUTH, authorities) // ["ROLE_MEMBER", "ROLE_ADMIN", "ROLE_EDITOR"]
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMillis))
                .signWith(key) // 서명
                .compact();
    }

    // Claim 파싱 및 검증
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token) //header, payload, signature 분리 후 디코딩 후 json 파싱
                // 검증 성공 시 Jwt<Claims> 반환
                .getPayload(); // payload만 추출
    }

    public Long getMemberId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }


    // claims중에서 auth를 꺼내와서 해당 토큰의 ROLE확인 -> ["ROLE_USER"]
    @SuppressWarnings("unchecked")
    public List<String> getAuthorities(String token) {
        Object auth = getClaims(token).get(CLAIM_AUTH);
        if (auth == null) return List.of();
        return (List<String>) auth;
    }

    // validate 결과를 ErrorCode로 변환 002, 003에 대한 에러 코드를 알아야됨
    public ErrorCode validateAndGetErrorCode(String token) {
        try {
            getClaims(token);
            return null;
        } catch (ExpiredJwtException e) {
            return ErrorCode.EXPIRED_TOKEN; // A003
        } catch (JwtException | IllegalArgumentException e) {
            return ErrorCode.INVALID_TOKEN; // A002
        }
    }

    // 타입 검증 메서드
    public boolean isAccessToken(String token) {
        return "access".equals(getClaims(token).get(CLAIM_TYPE));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getClaims(token).get(CLAIM_TYPE));
    }

}
