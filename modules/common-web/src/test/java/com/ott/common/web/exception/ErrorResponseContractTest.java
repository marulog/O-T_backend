package com.ott.common.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

class ErrorResponseContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    void everyErrorCodeHasHttpStatusMapping() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(ErrorHttpStatusMapper.toHttpStatus(errorCode))
                    .as(errorCode.name())
                    .isNotNull();
        }
    }

    @Test
    void representativeMappingsMatchExistingContract() {
        assertThat(ErrorHttpStatusMapper.toHttpStatus(ErrorCode.INVALID_INPUT)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorHttpStatusMapper.toHttpStatus(ErrorCode.UNAUTHORIZED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorHttpStatusMapper.toHttpStatus(ErrorCode.INTERNAL_ERROR)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void errorResponseJsonContractIsStable() throws Exception {
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, "detail message");

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("code").asText()).isEqualTo("C001");
        assertThat(json.get("message").asText()).isEqualTo("입력값이 올바르지 않습니다");
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("detail").asText()).isEqualTo("detail message");
        assertThat(json.hasNonNull("timestamp")).isTrue();
    }

    @Test
    void businessExceptionDetailUsesMappedMessage_whenExceptionMessageContainsSensitiveText() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String rawDetail = "s3://private-bucket/origin/object-key.mp4 sql=select * from member";

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.CONTENTS_NOT_FOUND, rawDetail)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("B101");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.CONTENTS_NOT_FOUND.getMessage());
        assertThat(response.getBody().getDetail()).isEqualTo(ErrorCode.CONTENTS_NOT_FOUND.getMessage());
        assertThat(response.getBody().getDetail()).doesNotContain("private-bucket", "select * from member");
    }

    @Test
    void frameworkExceptionDetailUsesMappedMessage_whenExceptionMessageContainsRequestInternals() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("objectKey/private-path", "String")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("C002");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.MISSING_PARAMETER.getMessage());
        assertThat(response.getBody().getDetail()).isEqualTo(ErrorCode.MISSING_PARAMETER.getMessage());
        assertThat(response.getBody().getDetail()).doesNotContain("objectKey/private-path");
    }

    @Test
    void unhandledExceptionDetailUsesMappedMessage_whenExceptionMessageContainsInfrastructureText() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleException(
                new IllegalStateException("jdbc:mysql://internal-db:3306/ott password=secret")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("C999");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
        assertThat(response.getBody().getDetail()).isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
        assertThat(response.getBody().getDetail()).doesNotContain("internal-db", "password=secret");
    }
}
