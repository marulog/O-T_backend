package com.ott.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.common.core.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JwtAuthenticationEntryPointSecurityTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(objectMapper);

    @Test
    void invalidTokenAttributeWritesUnauthorizedErrorContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE, ErrorCode.INVALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String rawDetail = "bad token kid=/secrets/jwt/private.pem";

        entryPoint.commence(request, response, new BadCredentialsException(rawDetail));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("A002");
        assertThat(body.get("message").asText()).isEqualTo(ErrorCode.INVALID_TOKEN.getMessage());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo(ErrorCode.INVALID_TOKEN.getMessage());
        assertThat(body.get("detail").asText()).doesNotContain("private.pem");
        assertThat(body.hasNonNull("timestamp")).isTrue();
    }

    @Test
    void malformedErrorCodeAttributeFallsBackToUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE, "INVALID_TOKEN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new BadCredentialsException("missing auth header Authorization=Bearer secret-token")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("A001");
        assertThat(body.get("message").asText()).isEqualTo(ErrorCode.UNAUTHORIZED.getMessage());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo(ErrorCode.UNAUTHORIZED.getMessage());
        assertThat(body.get("detail").asText()).doesNotContain("secret-token");
    }
}
