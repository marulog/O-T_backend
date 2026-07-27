package com.ott.api_admin.auth.service;

import com.ott.api_admin.auth.dto.request.AdminLoginRequest;
import com.ott.api_admin.auth.dto.response.AdminLoginResponse;
import com.ott.api_admin.auth.dto.response.AdminTokenResponse;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.infra.db.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 관리자 로그인
     * 토큰은 Controller에서 쿠키로 세팅
     */
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        // 1. 이메일 + LOCAL provider로 회원 조회
        Member member = memberRepository.findByEmailAndProvider(request.getEmail(), Provider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 비밀번호 검증 -> 추후 비밀번호 암호화 하여 검증 예정
        String encodedPassword = member.getPassword();
        if (encodedPassword == null || !passwordEncoder.matches(request.getPassword(), encodedPassword)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 3. 권한 확인 (ADMIN, EDITOR만 허용)
        if (member.getRole() != Role.ADMIN && member.getRole() != Role.EDITOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 없습니다.");
        }

        // 4. JWT 생성
        List<String> authorities = List.of(member.getRole().getKey());
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), authorities);
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId(), authorities);

        // 5. refresh  token DB 저장
        member.updateRefreshToken(refreshToken);

        return AdminLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .memberId(member.getId())
                .role(member.getRole().name())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .build();
    }

    /**
     * Access 발급 시 + Refresh Token 재발급
     */
    @Transactional
    public AdminTokenResponse reissue(String refreshToken) {
        // 1. refresh token 검증
        ErrorCode errorCode = jwtTokenProvider.validateAndGetErrorCode(refreshToken);
        if (errorCode != null) {
            throw new BusinessException(errorCode);
        }

        // 추가: refresh 토큰 타입 검증
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 2. DB 토큰 일치 확인
        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!refreshToken.equals(member.getRefreshToken())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 3. 권한 재확인 (강등된 계정 차단)
        if (member.getRole() != Role.ADMIN && member.getRole() != Role.EDITOR) {
            member.clearRefreshToken();
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 없습니다.");
        }

        // 4. 새 토큰 발급
        List<String> authorities = List.of(member.getRole().getKey());
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId, authorities);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId, authorities);

        // 5. refresh token 갱신
        member.clearRefreshToken();
        member.updateRefreshToken(newRefreshToken);

        return new AdminTokenResponse(newAccessToken, newRefreshToken);
    }

    /**
     * 로그아웃 — DB refresh token 삭제
     */
    @Transactional
    public void logout(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        member.clearRefreshToken();
    }
}