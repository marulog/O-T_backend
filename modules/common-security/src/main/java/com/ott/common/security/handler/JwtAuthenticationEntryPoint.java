package com.ott.common.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.common.core.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 401 인증 안됨, 유효하지않음, 만료됨
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ERROR_CODE = "AUTH_ERROR_CODE";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        // Filter에서 받아온 002, 003 에러일 경우 해당 에러 사용
        Object attribute = request.getAttribute(ERROR_CODE);
        ErrorCode errorCode = (attribute instanceof ErrorCode) ? (ErrorCode) attribute : ErrorCode.UNAUTHORIZED;
        log.warn("JWT authentication failed: errorCode={}", errorCode.getCode());
        SecurityErrorResponse errorResponse = SecurityErrorResponse.of(errorCode, errorCode.getMessage());

        response.setStatus(SecurityHttpStatusMapper.toHttpStatus(errorCode));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
