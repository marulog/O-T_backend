package com.ott.common.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.common.core.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 403 권한 없음
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        log.warn("JWT access denied: errorCode={}", ErrorCode.FORBIDDEN.getCode());
        SecurityErrorResponse errorResponse = SecurityErrorResponse.of(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());

        response.setStatus(SecurityHttpStatusMapper.toHttpStatus(ErrorCode.FORBIDDEN));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
