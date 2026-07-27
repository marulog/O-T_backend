package com.ott.common.security.filter;

import com.ott.common.security.handler.JwtAuthenticationEntryPoint;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.core.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 들어오는 요청을 가로채서 토큰을 꺼내서 provider에게 검증을 요청
 * 토큰은 보통 Authorization 헤더 or accssToken 쿠키에서 꺼냄 // 현재는 쿠키에 httpOnly로 저장중
 * provider에서 토큰을 검증하고 검증이 성공하면  Authentication객체를 생성해서 SecurityContextHolder에 authentication 저장함
 * 이후 컨트롤러에서 authentication 받아서 사용함
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.auth.cookie.access-name:accessToken}")
    private String accessCookieName;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 토큰 꺼내옴
        String token = resolveToken(request);

        // 토큰을 검증하여 인증 없음, 만료됨, 유효x일 경우 에러코드를 저장, 검증 통과 시 Authentication 생성
        if(token != null) {
            ErrorCode errorCode = jwtTokenProvider.validateAndGetErrorCode(token);

            if(errorCode == null) {
                // refresh 토큰으로 API 접근 차단
                if(!jwtTokenProvider.isAccessToken(token)){
                    request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE, ErrorCode.INVALID_TOKEN);
                } else {
                    Long memberId = jwtTokenProvider.getMemberId(token);

                    // auth: ["ROLE_USER"]
                    List<String> authorities = jwtTokenProvider.getAuthorities(token);

                    // Authentication을 만듬 -> 민감한 정보 저장 x
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            memberId, // principal // 추후 UserDetails로 변경할 예정 아마도
                            null, // credentials
                            authorities.stream() // grantedAuthorities
                                    .map(SimpleGrantedAuthority::new)
                                    .toList()
                    );
                    // Authenication을 SecurityContext에 넣음
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } else {
                // 실패할 경우 해당 에러코드를 reqeust에 넣음
                request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE, errorCode);
            }
        }

        filterChain.doFilter(request, response);
    }

    // 토큰 빼오기
    private String resolveToken(HttpServletRequest request) {
        //Authorization 헤더에서 토큰 빼오기 시도
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }

        // 쿠키에서 accessToken 빼오기 시도
        if(request.getCookies() != null) {
            for(Cookie cookie : request.getCookies()) {
                if (accessCookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }


}
