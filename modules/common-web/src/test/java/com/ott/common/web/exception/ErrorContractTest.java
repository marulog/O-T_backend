package com.ott.common.web.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.NoHandlerFoundException;

class ErrorContractTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
            .setControllerAdvice(handler)
            .build();

    @Test
    void businessExceptionProducesContentsNotFoundPayloadWithSanitizedPublicDetail() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(
                new BusinessException(
                        ErrorCode.CONTENTS_NOT_FOUND,
                        "raw storage key=s3://private-bucket/origin.mp4 sql=select * from contents"
                )
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertStandardDetailPayload(body, "B101", ErrorCode.CONTENTS_NOT_FOUND.getMessage(), 404);
        assertThat(body.get("detail").asText()).isEqualTo(ErrorCode.CONTENTS_NOT_FOUND.getMessage());
        assertThat(body.get("detail").asText()).doesNotContain("private-bucket", "select * from contents");
    }

    @Test
    void validationFailureProducesInvalidInputPayloadWithFieldErrors() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new ValidationPayload("", 17), "payload");
        bindingResult.rejectValue("title", "NotBlank", null, "must not be blank");
        bindingResult.rejectValue("age", "Min", null, "must be greater than or equal to 18");
        Method method = ValidationController.class.getDeclaredMethod("create", ValidationPayload.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValidException(
                new MethodArgumentNotValidException(parameter, bindingResult)
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertBasePayload(body, "C001", ErrorCode.INVALID_INPUT.getMessage(), 400);
        assertThat(body.has("detail")).isFalse();
        assertThat(body.get("errors")).hasSize(2);
        assertThat(body.get("errors").get(0).get("field").asText()).isEqualTo("title");
        assertThat(body.get("errors").get(0).get("value").asText()).isEmpty();
        assertThat(body.get("errors").get(0).get("reason").asText()).isEqualTo("must not be blank");
        assertThat(body.get("errors").get(1).get("field").asText()).isEqualTo("age");
        assertThat(body.get("errors").get(1).get("value").asText()).isEqualTo("17");
        assertThat(body.get("errors").get(1).get("reason").asText()).isEqualTo("must be greater than or equal to 18");
    }

    @Test
    void invalidJsonProducesJsonParseErrorPayloadAndNotInternalError() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/contract/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andReturn()
                .getResponse();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(400);
        assertStandardDetailPayload(body, "C005", ErrorCode.JSON_PARSE_ERROR.getMessage(), 400);
        assertThat(body.get("code").asText()).isNotEqualTo("C999");
    }

    @Test
    void missingBodyProducesMissingBodyPayload() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/contract/body")
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(400);
        assertStandardDetailPayload(body, "C004", ErrorCode.MISSING_BODY.getMessage(), 400);
    }

    @Test
    void methodNotAllowedProducesMappedPayload() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/contract/post-only"))
                .andReturn()
                .getResponse();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(405);
        assertStandardDetailPayload(body, "C007", ErrorCode.METHOD_NOT_ALLOWED.getMessage(), 405);
    }

    @Test
    void missingParameterProducesMappedPayloadWithSanitizedPublicDetail() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameterException(
                new org.springframework.web.bind.MissingServletRequestParameterException(
                        "privateObjectKey",
                        "String"
                )
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertStandardDetailPayload(body, "C002", ErrorCode.MISSING_PARAMETER.getMessage(), 400);
        assertThat(body.get("detail").asText()).doesNotContain("privateObjectKey");
    }

    @Test
    void typeMismatchProducesMappedPayloadWithSanitizedPublicDetail() throws Exception {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "private-id",
                Integer.class,
                "id",
                null,
                new NumberFormatException("not-a-number")
        );

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentTypeMismatchException(exception);

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertStandardDetailPayload(body, "C003", ErrorCode.INVALID_TYPE.getMessage(), 400);
        assertThat(body.get("detail").asText()).doesNotContain("private-id", "not-a-number");
    }

    @Test
    void noHandlerFoundProducesResourceNotFoundPayload() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleNoHandlerFoundException(
                new NoHandlerFoundException("GET", "/private/path", null)
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertStandardDetailPayload(body, "C006", ErrorCode.RESOURCE_NOT_FOUND.getMessage(), 404);
    }

    @Test
    void directMethodNotAllowedHandlerProducesMappedPayload() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleHttpRequestMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET"))
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertStandardDetailPayload(body, "C007", ErrorCode.METHOD_NOT_ALLOWED.getMessage(), 405);
    }

    @Test
    void illegalArgumentProducesInvalidInputPayloadWithSanitizedPublicDetail() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("raw object key=/private/file.mp4")
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertStandardDetailPayload(body, "C001", ErrorCode.INVALID_INPUT.getMessage(), 400);
        assertThat(body.get("detail").asText()).doesNotContain("private/file.mp4");
    }

    @Test
    void unhandledExceptionProducesInternalErrorPayloadWithSanitizedPublicDetail() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleException(
                new IllegalStateException("jdbc:mysql://internal-db password=secret")
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertStandardDetailPayload(body, "C999", ErrorCode.INTERNAL_ERROR.getMessage(), 500);
        assertThat(body.get("detail").asText()).doesNotContain("internal-db", "password=secret");
    }

    @Test
    void constraintViolationProducesInvalidInputPayloadWithSanitizedPublicDetail() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolationException(
                new jakarta.validation.ConstraintViolationException(
                        "raw query token=secret",
                        Set.of()
                )
        );

        JsonNode body = toJson(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertStandardDetailPayload(body, "C001", ErrorCode.INVALID_INPUT.getMessage(), 400);
        assertThat(body.get("detail").asText()).doesNotContain("secret");
    }

    private JsonNode toJson(ErrorResponse response) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(response));
    }

    private static void assertStandardDetailPayload(
            JsonNode body,
            String code,
            String message,
            int status
    ) {
        assertBasePayload(body, code, message, status);
        assertThat(body.get("detail").asText()).isEqualTo(message);
        assertThat(body.has("errors")).isFalse();
    }

    private static void assertBasePayload(
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
    }

    @RestController
    private static class ContractController {

        @PostMapping("/contract/body")
        void body(@RequestBody ContractRequest request) {
        }

        @PostMapping("/contract/post-only")
        void postOnly() {
        }
    }

    private record ContractRequest(String title) {
    }

    private record ValidationPayload(String title, Integer age) {
    }

    private static class ValidationController {

        @SuppressWarnings("unused")
        void create(ValidationPayload payload) {
        }
    }
}
