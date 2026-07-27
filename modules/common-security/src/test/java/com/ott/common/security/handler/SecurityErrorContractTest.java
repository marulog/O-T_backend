package com.ott.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.common.core.error.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class SecurityErrorContractTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
    private final JwtAccessDeniedHandler accessDeniedHandler = new JwtAccessDeniedHandler(objectMapper);

    @ParameterizedTest(name = "{0} writes {1}/{3}")
    @MethodSource("entryPointFixtures")
    void authenticationEntryPointWritesCurrentPublicErrorContract(
            ErrorCode requestAttribute,
            String code,
            String message,
            int status
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (requestAttribute != null) {
            request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE, requestAttribute);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new BadCredentialsException("raw Authorization=Bearer secret-token kid=/private/key.pem")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertStandardSecurityPayload(body, code, message, status);
        assertThat(body.get("detail").asText()).doesNotContain("secret-token", "private/key.pem");
    }

    @ParameterizedTest(name = "{0} maps to security status {1}")
    @MethodSource("securityStatusFixtures")
    void securityStatusMapperLocksCurrentJwtStatusContract(ErrorCode errorCode, int status) {
        assertThat(SecurityHttpStatusMapper.toHttpStatus(errorCode)).isEqualTo(status);
    }

    @ParameterizedTest(name = "forbidden response writes {0}/{2}")
    @MethodSource("forbiddenFixture")
    void accessDeniedHandlerWritesCurrentForbiddenPublicErrorContract(
            String code,
            String message,
            int status
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("raw role=ADMIN object=s3://private-admin-bucket/file")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertStandardSecurityPayload(body, code, message, status);
        assertThat(body.get("detail").asText()).doesNotContain("private-admin-bucket", "role=ADMIN");
    }

    private static void assertStandardSecurityPayload(
            JsonNode body,
            String code,
            String message,
            int status
    ) {
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").asText()).isEqualTo(message);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.hasNonNull("timestamp")).isTrue();
        assertThat(body.get("detail").asText()).isEqualTo(message);
        assertThat(body.has("errors")).isFalse();
    }

    private static Stream<Arguments> entryPointFixtures() {
        return Stream.of(
                Arguments.of(null, "A001", ErrorCode.UNAUTHORIZED.getMessage(), 401),
                Arguments.of(ErrorCode.INVALID_TOKEN, "A002", ErrorCode.INVALID_TOKEN.getMessage(), 401),
                Arguments.of(ErrorCode.EXPIRED_TOKEN, "A003", ErrorCode.EXPIRED_TOKEN.getMessage(), 401)
        );
    }

    private static Stream<Arguments> securityStatusFixtures() {
        return Stream.of(
                Arguments.of(ErrorCode.UNAUTHORIZED, 401),
                Arguments.of(ErrorCode.INVALID_TOKEN, 401),
                Arguments.of(ErrorCode.EXPIRED_TOKEN, 401),
                Arguments.of(ErrorCode.FORBIDDEN, 403),
                Arguments.of(ErrorCode.INTERNAL_ERROR, 401)
        );
    }

    private static Stream<Arguments> forbiddenFixture() {
        return Stream.of(
                Arguments.of("A004", ErrorCode.FORBIDDEN.getMessage(), 403)
        );
    }
}
