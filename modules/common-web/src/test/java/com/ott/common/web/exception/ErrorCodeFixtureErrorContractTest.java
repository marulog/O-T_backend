package com.ott.common.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.common.core.error.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorCodeFixtureErrorContractTest {

    @ParameterizedTest(name = "{0} maps to {1}/{3}")
    @MethodSource("errorCodeFixtures")
    void everyCurrentErrorCodeNameCodeMessageAndHttpStatusIsStable(
            String enumName,
            String code,
            String message,
            int httpStatus
    ) {
        ErrorCode errorCode = ErrorCode.valueOf(enumName);

        assertThat(errorCode.name()).isEqualTo(enumName);
        assertThat(errorCode.getCode()).isEqualTo(code);
        assertThat(errorCode.getMessage()).isEqualTo(message);
        assertThat(ErrorHttpStatusMapper.toHttpStatus(errorCode).value()).isEqualTo(httpStatus);
    }

    private static Stream<Arguments> errorCodeFixtures() {
        return Stream.of(
                Arguments.of("INVALID_INPUT", "C001", "입력값이 올바르지 않습니다", 400),
                Arguments.of("MISSING_PARAMETER", "C002", "필수 파라미터가 없습니다", 400),
                Arguments.of("INVALID_TYPE", "C003", "타입이 올바르지 않습니다", 400),
                Arguments.of("MISSING_BODY", "C004", "요청 본문이 없습니다", 400),
                Arguments.of("JSON_PARSE_ERROR", "C005", "JSON 형식이 올바르지 않습니다", 400),
                Arguments.of("RESOURCE_NOT_FOUND", "C006", "리소스를 찾을 수 없습니다", 404),
                Arguments.of("METHOD_NOT_ALLOWED", "C007", "허용되지 않은 메서드입니다", 405),
                Arguments.of("INTERNAL_ERROR", "C999", "서버 오류가 발생했습니다", 500),
                Arguments.of("UNAUTHORIZED", "A001", "인증이 필요합니다", 401),
                Arguments.of("INVALID_TOKEN", "A002", "유효하지 않은 토큰입니다", 401),
                Arguments.of("EXPIRED_TOKEN", "A003", "만료된 토큰입니다", 401),
                Arguments.of("FORBIDDEN", "A004", "접근 권한이 없습니다", 403),
                Arguments.of("KAKAO_UNLINK_FAILED", "A005", "카카오 인증 서버에 접근할 수 없습니다", 502),
                Arguments.of("CLOUDFRONT_SIGNED_COOKIE_ISSUE_FAILED", "A006", "CloudFront signed cookie 발급에 실패했습니다.", 500),
                Arguments.of("CLOUDFRONT_SIGNED_COOKIE_CONFIG_INVALID", "A007", "CloudFront signed cookie 설정이 올바르지 않습니다.", 500),
                Arguments.of("CLOUDFRONT_PRIVATE_KEY_INVALID", "A008", "CloudFront private key 형식이 올바르지 않습니다.", 500),
                Arguments.of("CLOUDFRONT_POLICY_SIGN_FAILED", "A009", "CloudFront policy 서명에 실패했습니다.", 500),
                Arguments.of("USER_NOT_FOUND", "U001", "사용자를 찾을 수 없습니다", 404),
                Arguments.of("DUPLICATE_EMAIL", "U002", "이미 존재하는 이메일입니다", 409),
                Arguments.of("ACCESS_DENIED", "U003", "해당 리소스에 대한 접근 권한이 없습니다.", 403),
                Arguments.of("CONTENTS_NOT_FOUND", "B101", "콘텐츠를 찾을 수 없습니다", 404),
                Arguments.of("SERIES_NOT_FOUND", "B102", "시리즈를 찾을 수 없습니다", 404),
                Arguments.of("CATEGORY_NOT_FOUND", "B103", "카테고리를 찾을 수 없습니다", 404),
                Arguments.of("TAG_NOT_FOUND", "B104", "태그를 찾을 수 없습니다", 404),
                Arguments.of("MEDIA_NOT_FOUND", "B105", "미디어를 찾을 수 없습니다", 404),
                Arguments.of("COMMENT_NOT_FOUND", "B106", "댓글을 찾을 수 없습니다", 404),
                Arguments.of("BOOKMARK_NOT_FOUND", "B107", "북마크를 찾을 수 없습니다", 404),
                Arguments.of("EPISODE_NOT_REGISTERED", "B108", "아직 에피소드가 등록되지 않았습니다.", 404),
                Arguments.of("SHORT_FORM_NOT_FOUND", "B109", "숏폼을 찾을 수 없습니다", 404),
                Arguments.of("REFRESH_CARD_NOT_FOUND", "B110", "환기 카드를 찾을 수 없습니다", 404),
                Arguments.of("ACTIVE_REFRESH_CARD_NOT_FOUND", "B111", "현재 활성화된 환기 카드가 없습니다", 404),
                Arguments.of("MOOD_TAG_NOT_FOUND", "B112", "감정 태그를 찾을 수 없습니다", 404),
                Arguments.of("SEARCH_KEYWORD_TOO_SHORT", "B201", "검색어는 최소 2글자 이상이어야 합니다", 400),
                Arguments.of("DUPLICATE_TAG_IN_LIST", "B202", "태그 목록에 중복된 값이 있습니다", 400),
                Arguments.of("INVALID_TAG_SELECTION", "B203", "카테고리에 맞지 않는 태그가 포함되어 있습니다", 400),
                Arguments.of("INVALID_ROLE_CHANGE", "B204", "허용되지 않는 역할 변경입니다", 400),
                Arguments.of("COMMENT_FORBIDDEN", "B205", "본인이 작성한 댓글만 수정/삭제할 수 있습니다", 403),
                Arguments.of("INVALID_PLAYLIST_SOURCE", "B206", "재생목록 소스(source)는 필수값입니다", 400),
                Arguments.of("CONTENTS_ORIGIN_OBJECT_KEY_MISMATCH", "B207", "contents objectKey가 원본 영상 URL과 일치하지 않습니다", 400),
                Arguments.of("SHORTFORM_ORIGIN_OBJECT_KEY_MISMATCH", "B208", "shortform objectKey가 원본 영상 URL과 일치하지 않습니다", 400),
                Arguments.of("ETAG_LIST_INVALID", "B209", "multipart eTag 목록이 유효하지 않습니다", 400),
                Arguments.of("UNSUPPORTED_IMAGE_EXTENSION", "B301", "지원하지 않는 이미지 확장자입니다", 400),
                Arguments.of("UNSUPPORTED_VIDEO_EXTENSION", "B302", "지원하지 않는 동영상 확장자입니다", 400),
                Arguments.of("INVALID_FILE_EXTENSION", "B303", "파일 확장자가 올바르지 않습니다", 400),
                Arguments.of("INVALID_TAG_CATEGORY", "B401", "유효하지 않은 태그 카테고리입니다", 400),
                Arguments.of("SHORTFORM_ORIGIN_MEDIA_NOT_FOUND", "B402", "쇼츠의 원본 미디어를 찾을 수 없습니다", 404),
                Arguments.of("INVALID_SHORTFORM_TARGET", "B403", "seriesId와 contentsId 중 하나만 제공해야 합니다", 400),
                Arguments.of("INVALID_SHORTFORM_CONTENTS_TARGET", "B404", "시리즈에 속한 콘텐츠는 숏폼 원본으로 선택할 수 없습니다", 400),
                Arguments.of("INVALID_REQUEST_FOR_SERIES_PLAYLIST", "B405", "해당 콘텐츠는 시리즈 전용 API를 사용해주세요", 400),
                Arguments.of("RADAR_PREFERENCE_NOT_FOUND", "B406", "레이더 차트 설정을 찾을 수 없습니다", 404),
                Arguments.of("RADAR_PREFERENCE_UNMODIFIABLE", "B407", "총합 100점을 모두 사용해야 레이더 차트 설정이 가능합니다", 404),
                Arguments.of("INGEST_JOB_NOT_FOUND", "B408", "IngestJob을 찾을 수 없습니다.", 404),
                Arguments.of("INGEST_COMMAND_NOT_FOUND", "B409", "IngestCommand를 찾을 수 없습니다.", 404),
                Arguments.of("ALREADY_INGESTED", "B410", "이미 트랜스코딩 작업이 등록된 미디어입니다", 409),
                Arguments.of("STRATEGY_NOT_FOUND", "S001", "적절한 재생목록 전략을 찾을 수 없습니다", 500)
        );
    }
}
