package com.ott.api_admin.shortform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.shortform.dto.request.ShortFormUpdateRequest;
import com.ott.api_admin.shortform.dto.response.ShortFormUpdateResponse;
import com.ott.api_admin.shortform.mapper.BackOfficeShortFormMapper;
import com.ott.api_admin.shortform.service.BackOfficeShortFormReader;
import com.ott.api_admin.shortform.service.BackOfficeShortFormWriter;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.common.web.exception.ErrorCodeHttpStatusMapper;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.series.domain.Series;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.infra.db.outbox.repository.TranscodeOutboxRepository;
import com.ott.infra.db.series.repository.SeriesRepository;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackOfficeShortFormUploadActorAuthorizationContractTest {

    private static final Long OWNER_ID = 11L;
    private static final Long OTHER_EDITOR_ID = 22L;
    private static final Long SHORT_FORM_ID = 202L;
    private static final Long ORIGIN_ID = 303L;
    private static final Long ORIGIN_MEDIA_ID = 404L;
    private static final String OBJECT_KEY = "short-forms/202/origin.mp4";
    private static final String UPLOAD_ID = "upload-202";

    @Mock private BackOfficeShortFormMapper mapper;

    @Mock private MediaRepository mediaRepository;

    @Mock private MediaTagRepository mediaTagRepository;

    @Mock private SeriesRepository seriesRepository;

    @Mock private ContentsRepository contentsRepository;

    @Mock private ShortFormRepository shortFormRepository;

    @Mock private IngestJobRepository ingestJobRepository;

    @Mock private TranscodeOutboxRepository transcodeOutboxRepository;

    @Mock private UploadHelper uploadHelper;

    @InjectMocks
    private BackOfficeShortFormReader reader;

    @InjectMocks
    private BackOfficeShortFormWriter writer;

    @ParameterizedTest(name = "{0} may inspect shortform upload info")
    @MethodSource("allowedActors")
    void getShortFormUploadInfoAllowsAdminAndOwningEditor(String label, AdminActor actor) {
        ShortForm shortForm = shortFormOwnedBy(OWNER_ID);
        when(shortFormRepository.findWithMediaAndUploaderByShortFormId(SHORT_FORM_ID)).thenReturn(Optional.of(shortForm));
        when(uploadHelper.getMultipartPartCount(shortForm.getVideoSize())).thenReturn(8);

        int totalParts = reader.getShortFormUploadInfo(SHORT_FORM_ID, OBJECT_KEY, actor);

        assertThat(totalParts).isEqualTo(8);
        verify(uploadHelper).validateOriginObjectKey(
                OBJECT_KEY,
                shortForm.getOriginUrl(),
                ErrorCode.SHORTFORM_ORIGIN_OBJECT_KEY_MISMATCH);
    }

    @Test
    void getShortFormUploadInfoRejectsNonOwningEditorWithForbidden() {
        when(shortFormRepository.findWithMediaAndUploaderByShortFormId(SHORT_FORM_ID))
                .thenReturn(Optional.of(shortFormOwnedBy(OWNER_ID)));

        assertForbidden(() -> reader.getShortFormUploadInfo(
                SHORT_FORM_ID,
                OBJECT_KEY,
                actor(OTHER_EDITOR_ID, Role.EDITOR)));
    }

    @ParameterizedTest(name = "{0} may inspect shortform upload part urls")
    @MethodSource("allowedActors")
    void getShortFormOriginUploadPartUrlsAllowsAdminAndOwningEditor(String label, AdminActor actor) {
        ShortForm shortForm = shortFormOwnedBy(OWNER_ID);
        PageResult<UploadHelper.MultipartUploadPartUrl> partUrls = PageResult.of(
                PageMetadata.of(0, 1, 2),
                List.of(new UploadHelper.MultipartUploadPartUrl(1, "https://upload.example/part-1")));
        when(shortFormRepository.findWithMediaAndUploaderByShortFormId(SHORT_FORM_ID)).thenReturn(Optional.of(shortForm));
        when(uploadHelper.getMultipartPartCount(shortForm.getVideoSize())).thenReturn(2);
        when(uploadHelper.getMultipartPartUrls(OBJECT_KEY, UPLOAD_ID, 2, 0, 2)).thenReturn(partUrls);

        var response = reader.getShortFormOriginUploadPartUrls(SHORT_FORM_ID, OBJECT_KEY, UPLOAD_ID, 0, 2, actor);

        assertThat(response.getDataList()).hasSize(1);
        assertThat(response.getDataList().getFirst().partNumber()).isEqualTo(1);
        assertThat(response.getPageMetadata()).isSameAs(partUrls.getPageMetadata());
        verify(uploadHelper).validateOriginObjectKey(
                OBJECT_KEY,
                shortForm.getOriginUrl(),
                ErrorCode.SHORTFORM_ORIGIN_OBJECT_KEY_MISMATCH);
    }

    @Test
    void getShortFormOriginUploadPartUrlsRejectsNonOwningEditorWithForbidden() {
        when(shortFormRepository.findWithMediaAndUploaderByShortFormId(SHORT_FORM_ID))
                .thenReturn(Optional.of(shortFormOwnedBy(OWNER_ID)));

        assertForbidden(() -> reader.getShortFormOriginUploadPartUrls(
                SHORT_FORM_ID,
                OBJECT_KEY,
                UPLOAD_ID,
                0,
                2,
                actor(OTHER_EDITOR_ID, Role.EDITOR)));
    }

    @ParameterizedTest(name = "{0} may update shortform upload metadata")
    @MethodSource("allowedActors")
    void updateShortFormUploadAllowsAdminAndOwningEditor(String label, AdminActor actor) {
        ShortForm shortForm = shortFormOwnedBy(OWNER_ID);
        Series origin = Series.builder()
                .id(ORIGIN_ID)
                .media(mediaOwnedBy(ORIGIN_MEDIA_ID, OWNER_ID))
                .actors("actor")
                .build();
        ShortFormUpdateResponse expected = new ShortFormUpdateResponse(
                SHORT_FORM_ID,
                "poster-key",
                "thumbnail-key",
                "https://upload.example/poster",
                "https://upload.example/thumbnail");
        when(shortFormRepository.findWithMediaAndUploaderByShortFormId(SHORT_FORM_ID)).thenReturn(Optional.of(shortForm));
        when(seriesRepository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin));
        when(uploadHelper.prepareImageUpdate(
                "short-forms",
                SHORT_FORM_ID,
                "poster.jpg",
                null,
                "poster",
                "thumbnail"))
                .thenReturn(new UploadHelper.ImageUpdateUploadResult(
                        "poster-key",
                        "thumbnail-key",
                        "https://upload.example/poster",
                        "https://upload.example/thumbnail",
                        "next-poster",
                        "next-thumbnail"));
        when(mediaTagRepository.findWithTagAndCategoryByMediaId(ORIGIN_MEDIA_ID)).thenReturn(List.of());
        when(mapper.toShortFormUpdateResponse(
                eq(SHORT_FORM_ID),
                eq("poster-key"),
                eq("thumbnail-key"),
                eq("https://upload.example/poster"),
                eq("https://upload.example/thumbnail")))
                .thenReturn(expected);

        ShortFormUpdateResponse actual = writer.updateShortFormUpload(
                SHORT_FORM_ID,
                updateRequest(),
                actor);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void updateShortFormUploadRejectsNonOwningEditorWithForbidden() {
        when(shortFormRepository.findWithMediaAndUploaderByShortFormId(SHORT_FORM_ID))
                .thenReturn(Optional.of(shortFormOwnedBy(OWNER_ID)));

        assertForbidden(() -> writer.updateShortFormUpload(
                SHORT_FORM_ID,
                updateRequest(),
                actor(OTHER_EDITOR_ID, Role.EDITOR)));
    }

    private static Stream<Arguments> allowedActors() {
        return Stream.of(
                Arguments.of("ADMIN", actor(OTHER_EDITOR_ID, Role.ADMIN)),
                Arguments.of("owning EDITOR", actor(OWNER_ID, Role.EDITOR))
        );
    }

    private static void assertForbidden(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getErrorCode().getCode()).isEqualTo("A004");
                    assertThat(ErrorCodeHttpStatusMapper.toHttpStatus(exception.getErrorCode()).value()).isEqualTo(403);
                });
    }

    private static ShortFormUpdateRequest updateRequest() {
        return new ShortFormUpdateRequest(
                ORIGIN_ID,
                MediaType.SERIES,
                "updated short",
                "updated description",
                PublicStatus.PUBLIC,
                "poster.jpg");
    }

    private static AdminActor actor(Long memberId, Role role) {
        return new AdminActor(memberId, Set.of(role.getKey()));
    }

    private static ShortForm shortFormOwnedBy(Long ownerId) {
        return ShortForm.builder()
                .id(SHORT_FORM_ID)
                .media(mediaOwnedBy(101L, ownerId))
                .duration(30)
                .videoSize(1200)
                .originUrl(OBJECT_KEY)
                .masterPlaylistUrl("short-forms/202/master.m3u8")
                .build();
    }

    private static Media mediaOwnedBy(Long mediaId, Long ownerId) {
        return Media.builder()
                .id(mediaId)
                .uploader(member(ownerId))
                .title("short")
                .description("description")
                .posterUrl("poster")
                .thumbnailUrl("thumbnail")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(MediaType.SHORT_FORM)
                .publicStatus(PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
    }

    private static Member member(Long id) {
        return Member.builder()
                .id(id)
                .email("owner@example.com")
                .nickname("owner")
                .role(Role.EDITOR)
                .provider(Provider.KAKAO)
                .build();
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke();
    }
}
