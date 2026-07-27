package com.ott.common.security.handler;

import com.ott.common.core.error.ErrorCode;

final class SecurityHttpStatusMapper {

    private SecurityHttpStatusMapper() {
    }

    static int toHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case UNAUTHORIZED, INVALID_TOKEN, EXPIRED_TOKEN -> 401;
            case FORBIDDEN -> 403;
            default -> 401;
        };
    }
}
