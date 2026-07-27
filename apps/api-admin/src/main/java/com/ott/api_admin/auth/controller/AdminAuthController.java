package com.ott.api_admin.auth.controller;

import com.ott.api_admin.auth.cdn.CloudFrontSignedCookieService;
import com.ott.api_admin.auth.dto.request.AdminLoginRequest;
import com.ott.api_admin.auth.dto.response.AdminLoginResponse;
import com.ott.api_admin.auth.dto.response.AdminTokenResponse;
import com.ott.api_admin.auth.service.AdminAuthService;
import com.ott.common.security.util.CookieUtil;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.web.response.SuccessResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/back-office")
@RequiredArgsConstructor
public class AdminAuthController implements AdminAuthApi {

    private final AdminAuthService adminAuthService;
    private final CookieUtil cookie;
    private final CloudFrontSignedCookieService cloudFrontSignedCookieService;

    @Value("${jwt.access-token-expiry}")
    private int accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private int refreshTokenExpiry;

    @Value("${app.auth.cookie.access-name:adminAccessToken}")
    private String accessCookieName;

    @Value("${app.auth.cookie.refresh-name:adminRefreshToken}")
    private String refreshCookieName;

    @Override
    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<AdminLoginResponse>> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletResponse response) {

        AdminLoginResponse loginResponse = adminAuthService.login(request);

        // TODO: 2026-04-06 이후 삭제 (레거시 공유 도메인 쿠키 마이그레이션 완료)
//        cookie.deleteCookie(response, "accessToken", "openthetaste.cloud");
//        cookie.deleteCookie(response, "refreshToken", "openthetaste.cloud");

        // 둘 다 쿠키로
        cookie.addCookie(response, accessCookieName, loginResponse.getAccessToken(), accessTokenExpiry);
        cookie.addCookie(response, refreshCookieName, loginResponse.getRefreshToken(), refreshTokenExpiry);
        cloudFrontSignedCookieService.addSignedCookies(response);

        // Body에는 memberId, role만 (토큰은 @JsonIgnore)
        return SuccessResponse.of(loginResponse).asHttp(HttpStatus.OK);
    }

    @Override
    @PostMapping("/reissue")
    public ResponseEntity<Void> reissue(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractCookie(request, refreshCookieName);
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        AdminTokenResponse tokenResponse = adminAuthService.reissue(refreshToken);

        // TODO: 2026-04-06 이후 삭제 (레거시 공유 도메인 쿠키 마이그레이션 완료)
//        cookie.deleteCookie(response, "accessToken", "openthetaste.cloud");
//        cookie.deleteCookie(response, "refreshToken", "openthetaste.cloud");

        cookie.addCookie(response, accessCookieName, tokenResponse.getAccessToken(), accessTokenExpiry);
        cookie.addCookie(response, refreshCookieName, tokenResponse.getRefreshToken(), refreshTokenExpiry);
        cloudFrontSignedCookieService.addSignedCookies(response);

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletResponse response) {

        Long memberId = (Long) authentication.getPrincipal();
        adminAuthService.logout(memberId);

        // TODO: 2026-04-06 이후 삭제 (레거시 공유 도메인 쿠키 마이그레이션 완료)
//        cookie.deleteCookie(response, "accessToken", "openthetaste.cloud");
//        cookie.deleteCookie(response, "refreshToken", "openthetaste.cloud");

        cookie.deleteCookie(response, accessCookieName);
        cookie.deleteCookie(response, refreshCookieName);
        cloudFrontSignedCookieService.clearSignedCookies(response);

        return ResponseEntity.noContent().build();
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
