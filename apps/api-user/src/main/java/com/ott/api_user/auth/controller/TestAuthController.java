package com.ott.api_user.auth.controller;


import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.web.exception.BusinessException;
import com.ott.common.web.exception.ErrorCode;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 *  테스트 전용 컨트롤러 - 운영 환경 배포 금지
 *
 * k6 부하 테스트에서 VU별로 다른 유저 토큰을 발급받기 위한 엔드포인트.
 * @Profile("!prod")로 운영 프로필에서는 빈 등록 자체가 차단됨.
 *
 * 사용법:
 *   POST /test/auth/token/{memberId}
 *   응답: { "accessToken": "eyJhbGc..." }
 */
@Slf4j
@RestController
@RequestMapping("/test/auth")
@RequiredArgsConstructor
@Profile("!prod")
public class TestAuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;

    @PostMapping("/token/{memberId}")
    public ResponseEntity<Map<String, String>> issueTestToken(@PathVariable Long memberId) {
        log.warn("[TEST-AUTH] 테스트용 토큰 발급 요청: memberId={}", memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String accessToken = jwtTokenProvider.createAccessToken(
                member.getId(),
                List.of(member.getRole().getKey())
        );

        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }
}