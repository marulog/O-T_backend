package com.ott.api_user.auth.controller;

import com.ott.api_user.auth.cdn.CloudFrontSignedCookieService;
import com.ott.api_user.auth.dto.TokenResponse;
import com.ott.api_user.auth.service.AuthService;
import com.ott.common.security.util.CookieUtil;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final CloudFrontSignedCookieService cloudFrontSignedCookieService;

    @Value("${jwt.access-token-expiry}")
    private int accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private int refreshTokenExpiry;

    @Value("${app.auth.cookie.access-name:userAccessToken}")
    private String accessCookieName;

    @Value("${app.auth.cookie.refresh-name:userRefreshToken}")
    private String refreshCookieName;

    // Access Token 재발급
    @PostMapping("reissue")
    public ResponseEntity<Void> reissue(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractCookie(request, refreshCookieName);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // access + refresh 재발급 -> 보안성 측면
        TokenResponse tokenResponse = authService.reissue(refreshToken);

        // TODO: 2026-04-06 이후 삭제 (레거시 공유 도메인 쿠키 마이그레이션 완료)
//        cookieUtil.deleteCookie(response, "accessToken", "openthetaste.cloud");
//        cookieUtil.deleteCookie(response, "refreshToken", "openthetaste.cloud");

        cookieUtil.addCookie(response, accessCookieName, tokenResponse.getAccessToken(), accessTokenExpiry);
        cookieUtil.addCookie(response, refreshCookieName, tokenResponse.getRefreshToken(), refreshTokenExpiry);
        cloudFrontSignedCookieService.addSignedCookies(response);

        return ResponseEntity.noContent().build();
    }

    /**
     * 로그아웃
     * DB는 refreshToken 삭제
     * 쿠키는 Controller에서 직접 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletResponse response) {

        Long memberId = (Long) authentication.getPrincipal();
        authService.logout(memberId);

//        // TODO: 2026-04-06 이후 삭제 (레거시 공유 도메인 쿠키 마이그레이션 완료)
//        cookieUtil.deleteCookie(response, "accessToken", "openthetaste.cloud");
//        cookieUtil.deleteCookie(response, "refreshToken", "openthetaste.cloud");

        // 쿠키 삭제
        cookieUtil.deleteCookie(response, accessCookieName);
        cookieUtil.deleteCookie(response, refreshCookieName);
        cloudFrontSignedCookieService.clearSignedCookies(response);

        return ResponseEntity.noContent().build();
    }



    // 쿠키에 대한 접근은 HTTP고 서비스로 내려가면 안되기 때문에 Controller에서 구현
    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie: request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
