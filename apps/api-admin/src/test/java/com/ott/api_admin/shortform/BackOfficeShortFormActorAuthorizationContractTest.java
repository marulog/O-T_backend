package com.ott.api_admin.shortform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.shortform.controller.BackOfficeShortFormController;
import com.ott.api_admin.shortform.dto.response.ShortFormDetailResponse;
import com.ott.api_admin.shortform.mapper.BackOfficeShortFormMapper;
import com.ott.api_admin.shortform.service.BackOfficeShortFormReader;
import com.ott.api_admin.shortform.service.BackOfficeShortFormService;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.web.exception.ErrorCodeHttpStatusMapper;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.infra.db.series.repository.SeriesRepository;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class BackOfficeShortFormActorAuthorizationContractTest {

    private static final Long OWNER_ID = 11L;
    private static final Long OTHER_EDITOR_ID = 22L;
    private static final Long MEDIA_ID = 101L;
    private static final Long SHORT_FORM_ID = 202L;

    @Mock
    private BackOfficeShortFormMapper backOfficeShortFormMapper;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private MediaTagRepository mediaTagRepository;

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private ContentsRepository contentsRepository;

    @Mock
    private ShortFormRepository shortFormRepository;

    @Mock
    private UploadHelper uploadHelper;

    @Mock
    private BackOfficeShortFormService backOfficeShortFormService;

    @InjectMocks
    private BackOfficeShortFormReader reader;

    @InjectMocks
    private BackOfficeShortFormController controller;

    @Test
    void getShortFormDetailAllowsAdminWhenAdminDoesNotOwnMedia() {
        ShortForm shortForm = shortFormOwnedBy(OWNER_ID);
        ShortFormDetailResponse expected = detailResponse();
        when(shortFormRepository.findWithMediaAndUploaderByMediaId(MEDIA_ID)).thenReturn(Optional.of(shortForm));
        when(mediaTagRepository.findWithTagAndCategoryByMediaId(MEDIA_ID)).thenReturn(List.of());
        when(backOfficeShortFormMapper.toShortFormDetailResponse(
                eq(shortForm), eq(shortForm.getMedia()), eq("owner"), isNull(), isNull(), isNull(), eq(List.of())))
                .thenReturn(expected);

        ShortFormDetailResponse actual = reader.getShortFormDetail(MEDIA_ID, actor(OTHER_EDITOR_ID, Role.ADMIN));

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void getShortFormDetailAllowsOwningEditor() {
        ShortForm shortForm = shortFormOwnedBy(OWNER_ID);
        ShortFormDetailResponse expected = detailResponse();
        when(shortFormRepository.findWithMediaAndUploaderByMediaId(MEDIA_ID)).thenReturn(Optional.of(shortForm));
        when(mediaTagRepository.findWithTagAndCategoryByMediaId(MEDIA_ID)).thenReturn(List.of());
        when(backOfficeShortFormMapper.toShortFormDetailResponse(
                eq(shortForm), eq(shortForm.getMedia()), eq("owner"), isNull(), isNull(), isNull(), eq(List.of())))
                .thenReturn(expected);

        ShortFormDetailResponse actual = reader.getShortFormDetail(MEDIA_ID, actor(OWNER_ID, Role.EDITOR));

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void getShortFormDetailRejectsNonOwningEditorWithForbidden() {
        ShortForm shortForm = shortFormOwnedBy(OWNER_ID);
        when(shortFormRepository.findWithMediaAndUploaderByMediaId(MEDIA_ID)).thenReturn(Optional.of(shortForm));

        assertThatThrownBy(() -> reader.getShortFormDetail(MEDIA_ID, actor(OTHER_EDITOR_ID, Role.EDITOR)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getErrorCode().getCode()).isEqualTo("A004");
                    assertThat(ErrorCodeHttpStatusMapper.toHttpStatus(exception.getErrorCode()).value()).isEqualTo(403);
                });
    }

    @Test
    void getShortFormListLeavesAdminUnscopedAndScopesEditorToPrincipal() {
        PageRequest firstPage = PageRequest.of(0, 10);
        when(mediaRepository.findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(
                firstPage, MediaType.SHORT_FORM, "clip", PublicStatus.PUBLIC, null))
                .thenReturn(new PageImpl<>(List.of(), firstPage, 0));
        when(mediaRepository.findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(
                firstPage, MediaType.SHORT_FORM, "clip", PublicStatus.PUBLIC, OWNER_ID))
                .thenReturn(new PageImpl<>(List.of(), firstPage, 0));

        reader.getShortFormList(0, 10, "clip", PublicStatus.PUBLIC, actor(OTHER_EDITOR_ID, Role.ADMIN));
        reader.getShortFormList(0, 10, "clip", PublicStatus.PUBLIC, actor(OWNER_ID, Role.EDITOR));

        verify(mediaRepository).findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(
                firstPage, MediaType.SHORT_FORM, "clip", PublicStatus.PUBLIC, null);
        verify(mediaRepository).findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(
                firstPage, MediaType.SHORT_FORM, "clip", PublicStatus.PUBLIC, OWNER_ID);
    }

    @Test
    void controllerForwardsAuthenticationBoundaryToShortFormService() {
        Authentication authentication = authentication(OWNER_ID, Role.EDITOR);
        AdminActor actor = actor(OWNER_ID, Role.EDITOR);
        ShortFormDetailResponse expected = detailResponse();
        when(backOfficeShortFormService.getShortFormDetail(MEDIA_ID, actor)).thenReturn(expected);

        var response = controller.getShortFormDetail(MEDIA_ID, authentication);

        assertThat(response.getBody().getData()).isSameAs(expected);
        verify(backOfficeShortFormService).getShortFormDetail(MEDIA_ID, actor);
    }

    private static AdminActor actor(Long memberId, Role role) {
        return new AdminActor(memberId, Set.of(role.getKey()));
    }

    private static Authentication authentication(Long principal, Role role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role.getKey()))
        );
    }

    private static ShortForm shortFormOwnedBy(Long ownerId) {
        return ShortForm.builder()
                .id(SHORT_FORM_ID)
                .media(mediaOwnedBy(ownerId))
                .duration(30)
                .videoSize(1200)
                .originUrl("short-forms/202/origin.mp4")
                .masterPlaylistUrl("short-forms/202/master.m3u8")
                .build();
    }

    private static Media mediaOwnedBy(Long ownerId) {
        return Media.builder()
                .id(MEDIA_ID)
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

    private static ShortFormDetailResponse detailResponse() {
        return new ShortFormDetailResponse(
                SHORT_FORM_ID,
                "poster",
                "short",
                "description",
                null,
                null,
                null,
                "owner",
                30,
                1200,
                null,
                List.of(),
                PublicStatus.PUBLIC,
                0L,
                null
        );
    }
}
