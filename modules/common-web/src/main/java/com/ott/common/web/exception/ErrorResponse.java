package com.ott.common.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ott.common.core.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "실패 Response")
public class ErrorResponse {

    @Schema(description = "성공 여부", defaultValue = "false")
    private final boolean success = false;

    @Schema(description = "에러 구분 코드")
    private String code;

    @Schema(description = "에러 메시지")
    private String message;

    @Schema(description = "HTTP 상태 코드")
    private int status;

    @Schema(description = "에러 발생 시각")
    private LocalDateTime timestamp;

    @Schema(description = "필드별 유효성 검증 오류")
    private List<FieldError> errors;

    @Schema(description = "에러 상세 정보")
    private String detail;

    private ErrorResponse(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.status = ErrorHttpStatusMapper.toHttpStatus(errorCode).value();
        this.timestamp = LocalDateTime.now();
    }

    private ErrorResponse(ErrorCode errorCode, String detail) {
        this(errorCode);
        this.detail = detail;
    }

    private ErrorResponse(ErrorCode errorCode, List<FieldError> errors) {
        this(errorCode);
        this.errors = errors;
    }

    public static ErrorResponse of(ErrorCode errorCode, String detail) {
        return new ErrorResponse(errorCode, detail);
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return new ErrorResponse(errorCode, FieldError.of(bindingResult));
    }

    @Getter
    public static class FieldError {
        private final String field;
        private final String value;
        private final String reason;

        @Builder
        private FieldError(String field, String value, String reason) {
            this.field = field;
            this.value = value;
            this.reason = reason;
        }

        public static List<FieldError> of(String field, String value, String reason) {
            return List.of(new FieldError(field, value, reason));
        }

        private static List<FieldError> of(BindingResult bindingResult) {
            return bindingResult.getFieldErrors().stream()
                    .map(error -> FieldError.builder()
                            .field(error.getField())
                            .value(error.getRejectedValue() == null ? "" : error.getRejectedValue().toString())
                            .reason(error.getDefaultMessage())
                            .build())
                    .collect(Collectors.toList());
        }
    }
}
