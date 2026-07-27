package com.ott.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.common.core.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class JwtAccessDeniedHandlerSecurityTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler(objectMapper);

    @Test
    void accessDeniedWritesForbiddenErrorContract() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String rawDetail = "not enough role for s3://private-admin-bucket/object-key";

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException(rawDetail)
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("A004");
        assertThat(body.get("message").asText()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("detail").asText()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
        assertThat(body.get("detail").asText()).doesNotContain("private-admin-bucket", "object-key");
        assertThat(body.hasNonNull("timestamp")).isTrue();
    }
}
