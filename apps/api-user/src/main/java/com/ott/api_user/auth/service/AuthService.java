package com.ott.api_user.auth.service;

import com.ott.api_user.auth.dto.TokenResponse;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JWT 토큰 관리하는 클래스 -> 재발급과 로그아웃 구현
 * 모든 소셜 로그인 공통으로 사용 -> 현재는 카카오만 사용
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Access Token 재발급
     */
    public TokenResponse reissue(String refreshToken) {
        // refresh 토큰 유효성 검증
        ErrorCode errorCode = jwtTokenProvider.validateAndGetErrorCode(refreshToken);

        if (errorCode != null) {
            throw new BusinessException(errorCode);
        }

        // 추가: refresh 토큰 타입 검증
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // DB에 저장된 토큰과 일치 여부 확인
        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        Member member = findMemberById(memberId);

        if (!refreshToken.equals(member.getRefreshToken())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 권한 추출
        List<String> authorities = List.of(member.getRole().getKey());

        // access + refresh 재발급
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId, authorities);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId, authorities);

        // refreshToken 갱신 및 이전 토큰 폐기
        member.clearRefreshToken();
        member.updateRefreshToken(newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);

    }

    /**
     * 로그아웃 - Refresh 토큰 삭제
     */
    public void logout(Long memberId) {
        Member member = findMemberById(memberId);
        member.clearRefreshToken();
    }

    public String issueTestAccessToken(Long memberId) {
        Member member = findMemberById(memberId);
        return jwtTokenProvider.createAccessToken(
                member.getId(),
                List.of(member.getRole().getKey())
        );
    }

    // Optipnal 처리를 위해 사용
    private Member findMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
