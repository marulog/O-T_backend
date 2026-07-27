package com.ott.common.security.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ott.common.core.error.ErrorCode;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
final class SecurityErrorResponse {

    private final boolean success = false;
    private final String code;
    private final String message;
    private final int status;
    private final LocalDateTime timestamp;
    private final String detail;

    private SecurityErrorResponse(ErrorCode errorCode, String detail) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.status = SecurityHttpStatusMapper.toHttpStatus(errorCode);
        this.timestamp = LocalDateTime.now();
        this.detail = detail;
    }

    static SecurityErrorResponse of(ErrorCode errorCode, String detail) {
        return new SecurityErrorResponse(errorCode, detail);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getStatus() {
        return status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDetail() {
        return detail;
    }
}
