package com.ott.common.core.error;

/**
 * 코드 체계:
 * - C0XX: Common (공통)
 * - A0XX: Auth (인증/인가)
 * - U0XX: User (사용자)
 * - B0XX: Video/Content 등 비즈니스 룰
 * - S0XX: Server (서버)
 */
public enum ErrorCode {

    // ========== Common (C) - 공통 ==========
    INVALID_INPUT("C001", "입력값이 올바르지 않습니다"),
    MISSING_PARAMETER("C002", "필수 파라미터가 없습니다"),
    INVALID_TYPE("C003", "타입이 올바르지 않습니다"),
    MISSING_BODY("C004", "요청 본문이 없습니다"),
    JSON_PARSE_ERROR("C005", "JSON 형식이 올바르지 않습니다"),

    RESOURCE_NOT_FOUND("C006", "리소스를 찾을 수 없습니다"),

    METHOD_NOT_ALLOWED("C007", "허용되지 않은 메서드입니다"),

    INTERNAL_ERROR("C999", "서버 오류가 발생했습니다"),

    // ========== Auth (A) - 인증/인가 ==========
    UNAUTHORIZED("A001", "인증이 필요합니다"),
    INVALID_TOKEN("A002", "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN("A003", "만료된 토큰입니다"),
    FORBIDDEN("A004", "접근 권한이 없습니다"),
    KAKAO_UNLINK_FAILED("A005", "카카오 인증 서버에 접근할 수 없습니다"),
    CLOUDFRONT_SIGNED_COOKIE_ISSUE_FAILED("A006", "CloudFront signed cookie 발급에 실패했습니다."),
    CLOUDFRONT_SIGNED_COOKIE_CONFIG_INVALID("A007", "CloudFront signed cookie 설정이 올바르지 않습니다."),
    CLOUDFRONT_PRIVATE_KEY_INVALID("A008", "CloudFront private key 형식이 올바르지 않습니다."),
    CLOUDFRONT_POLICY_SIGN_FAILED("A009", "CloudFront policy 서명에 실패했습니다."),

    // ========== User (U) - 사용자 ==========
    USER_NOT_FOUND("U001", "사용자를 찾을 수 없습니다"),
    DUPLICATE_EMAIL("U002", "이미 존재하는 이메일입니다"),
    ACCESS_DENIED("U003", "해당 리소스에 대한 접근 권한이 없습니다."),


    // ========== Business (B) - 비즈니스 (조회 실패: 100번대) ==========
    CONTENTS_NOT_FOUND("B101", "콘텐츠를 찾을 수 없습니다"),
    SERIES_NOT_FOUND("B102", "시리즈를 찾을 수 없습니다"),
    CATEGORY_NOT_FOUND("B103", "카테고리를 찾을 수 없습니다"),
    TAG_NOT_FOUND("B104", "태그를 찾을 수 없습니다"),
    MEDIA_NOT_FOUND("B105", "미디어를 찾을 수 없습니다"),
    COMMENT_NOT_FOUND("B106", "댓글을 찾을 수 없습니다"),
    BOOKMARK_NOT_FOUND("B107", "북마크를 찾을 수 없습니다"),
    EPISODE_NOT_REGISTERED("B108", "아직 에피소드가 등록되지 않았습니다."),
    SHORT_FORM_NOT_FOUND("B109", "숏폼을 찾을 수 없습니다"),

    REFRESH_CARD_NOT_FOUND("B110", "환기 카드를 찾을 수 없습니다"),
    ACTIVE_REFRESH_CARD_NOT_FOUND("B111", "현재 활성화된 환기 카드가 없습니다"),
    MOOD_TAG_NOT_FOUND("B112", "감정 태그를 찾을 수 없습니다"),

    // ========== Business (B) - 비즈니스 (정책/유효성: 200번대) ==========
    SEARCH_KEYWORD_TOO_SHORT("B201", "검색어는 최소 2글자 이상이어야 합니다"),
    DUPLICATE_TAG_IN_LIST("B202", "태그 목록에 중복된 값이 있습니다"),
    INVALID_TAG_SELECTION("B203", "카테고리에 맞지 않는 태그가 포함되어 있습니다"),
    INVALID_ROLE_CHANGE("B204", "허용되지 않는 역할 변경입니다"),
    COMMENT_FORBIDDEN("B205", "본인이 작성한 댓글만 수정/삭제할 수 있습니다"),
    INVALID_PLAYLIST_SOURCE("B206", "재생목록 소스(source)는 필수값입니다"),

    CONTENTS_ORIGIN_OBJECT_KEY_MISMATCH("B207", "contents objectKey가 원본 영상 URL과 일치하지 않습니다"),
    SHORTFORM_ORIGIN_OBJECT_KEY_MISMATCH("B208", "shortform objectKey가 원본 영상 URL과 일치하지 않습니다"),
    ETAG_LIST_INVALID("B209", "multipart eTag 목록이 유효하지 않습니다"),

    // ========== Business (B) - 비즈니스 (미디어/파일 전용: 300번대) ==========
    UNSUPPORTED_IMAGE_EXTENSION("B301", "지원하지 않는 이미지 확장자입니다"),
    UNSUPPORTED_VIDEO_EXTENSION("B302", "지원하지 않는 동영상 확장자입니다"),
    INVALID_FILE_EXTENSION("B303", "파일 확장자가 올바르지 않습니다"),

    // ========== Business (B) - 비즈니스 (특수 도메인 규칙/태그/숏폼: 400번대) ==========
    INVALID_TAG_CATEGORY("B401", "유효하지 않은 태그 카테고리입니다"),
    SHORTFORM_ORIGIN_MEDIA_NOT_FOUND("B402", "쇼츠의 원본 미디어를 찾을 수 없습니다"),
    INVALID_SHORTFORM_TARGET("B403", "seriesId와 contentsId 중 하나만 제공해야 합니다"),
    INVALID_SHORTFORM_CONTENTS_TARGET("B404", "시리즈에 속한 콘텐츠는 숏폼 원본으로 선택할 수 없습니다"),
    INVALID_REQUEST_FOR_SERIES_PLAYLIST("B405", "해당 콘텐츠는 시리즈 전용 API를 사용해주세요"),

    RADAR_PREFERENCE_NOT_FOUND("B406", "레이더 차트 설정을 찾을 수 없습니다"),
    RADAR_PREFERENCE_UNMODIFIABLE("B407", "총합 100점을 모두 사용해야 레이더 차트 설정이 가능합니다"),

    INGEST_JOB_NOT_FOUND("B408", "IngestJob을 찾을 수 없습니다."),
    INGEST_COMMAND_NOT_FOUND("B409", "IngestCommand를 찾을 수 없습니다."),
    ALREADY_INGESTED("B410", "이미 트랜스코딩 작업이 등록된 미디어입니다"),

    // ========== Server (S) - 서버/시스템 ==========
    STRATEGY_NOT_FOUND("S001", "적절한 재생목록 전략을 찾을 수 없습니다");


    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
