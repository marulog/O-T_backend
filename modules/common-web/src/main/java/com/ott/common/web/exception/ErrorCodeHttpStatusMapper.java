package com.ott.common.web.exception;

import com.ott.common.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Deprecated(forRemoval = false)
public final class ErrorCodeHttpStatusMapper {

    private ErrorCodeHttpStatusMapper() {
    }

    public static HttpStatus toHttpStatus(ErrorCode errorCode) {
        return ErrorHttpStatusMapper.toHttpStatus(errorCode);
    }
}
