package com.ott.api_user.auth.controller;


import com.ott.api_user.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 *  테스트 전용 컨트롤러 - 운영 환경 배포 금지
 *
 * k6 부하 테스트에서 VU별로 다른 유저 토큰을 발급받기 위한 엔드포인트.
 * local-test-auth 프로필을 명시적으로 켠 로컬 환경에서만 빈이 등록됨.
 *
 * 사용법:
 *   POST /test/auth/token/{memberId}
 *   응답: { "accessToken": "eyJhbGc..." }
 */
@Slf4j
@RestController
@RequestMapping("/test/auth")
@RequiredArgsConstructor
@Profile("local-test-auth")
public class TestAuthController {

    private final AuthService authService;

    @PostMapping("/token/{memberId}")
    public ResponseEntity<Map<String, String>> issueTestToken(@PathVariable Long memberId) {
        log.warn("[TEST-AUTH] 테스트용 토큰 발급 요청: memberId={}", memberId);

        String accessToken = authService.issueTestAccessToken(memberId);

        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }
}
