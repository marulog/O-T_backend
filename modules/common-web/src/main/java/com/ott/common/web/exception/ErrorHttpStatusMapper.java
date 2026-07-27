package com.ott.common.web.exception;

import com.ott.common.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

public final class ErrorHttpStatusMapper {

    private ErrorHttpStatusMapper() {
    }

    public static HttpStatus toHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_INPUT,
                    MISSING_PARAMETER,
                    INVALID_TYPE,
                    MISSING_BODY,
                    JSON_PARSE_ERROR,
                    SEARCH_KEYWORD_TOO_SHORT,
                    DUPLICATE_TAG_IN_LIST,
                    INVALID_TAG_SELECTION,
                    INVALID_ROLE_CHANGE,
                    INVALID_PLAYLIST_SOURCE,
                    CONTENTS_ORIGIN_OBJECT_KEY_MISMATCH,
                    SHORTFORM_ORIGIN_OBJECT_KEY_MISMATCH,
                    ETAG_LIST_INVALID,
                    UNSUPPORTED_IMAGE_EXTENSION,
                    UNSUPPORTED_VIDEO_EXTENSION,
                    INVALID_FILE_EXTENSION,
                    INVALID_TAG_CATEGORY,
                    INVALID_SHORTFORM_TARGET,
                    INVALID_SHORTFORM_CONTENTS_TARGET,
                    INVALID_REQUEST_FOR_SERIES_PLAYLIST -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND,
                    USER_NOT_FOUND,
                    CONTENTS_NOT_FOUND,
                    SERIES_NOT_FOUND,
                    CATEGORY_NOT_FOUND,
                    TAG_NOT_FOUND,
                    MEDIA_NOT_FOUND,
                    COMMENT_NOT_FOUND,
                    BOOKMARK_NOT_FOUND,
                    EPISODE_NOT_REGISTERED,
                    SHORT_FORM_NOT_FOUND,
                    REFRESH_CARD_NOT_FOUND,
                    ACTIVE_REFRESH_CARD_NOT_FOUND,
                    MOOD_TAG_NOT_FOUND,
                    SHORTFORM_ORIGIN_MEDIA_NOT_FOUND,
                    RADAR_PREFERENCE_NOT_FOUND,
                    RADAR_PREFERENCE_UNMODIFIABLE,
                    INGEST_JOB_NOT_FOUND,
                    INGEST_COMMAND_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case UNAUTHORIZED,
                    INVALID_TOKEN,
                    EXPIRED_TOKEN -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN,
                    ACCESS_DENIED,
                    COMMENT_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case KAKAO_UNLINK_FAILED -> HttpStatus.BAD_GATEWAY;
            case DUPLICATE_EMAIL,
                    ALREADY_INGESTED -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR,
                    CLOUDFRONT_SIGNED_COOKIE_ISSUE_FAILED,
                    CLOUDFRONT_SIGNED_COOKIE_CONFIG_INVALID,
                    CLOUDFRONT_PRIVATE_KEY_INVALID,
                    CLOUDFRONT_POLICY_SIGN_FAILED,
                    STRATEGY_NOT_FOUND -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
