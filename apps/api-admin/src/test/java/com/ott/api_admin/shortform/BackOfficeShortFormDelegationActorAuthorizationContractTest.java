package com.ott.api_admin.shortform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.content.vo.IngestJobResult;
import com.ott.api_admin.publish.RabbitTranscodePublisher;
import com.ott.api_admin.shortform.controller.BackOfficeShortFormController;
import com.ott.api_admin.shortform.dto.request.ShortFormUpdateRequest;
import com.ott.api_admin.shortform.dto.request.ShortFormUploadRequest;
import com.ott.api_admin.shortform.dto.response.ShortFormDetailResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormListResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormUpdateResponse;
import com.ott.api_admin.shortform.dto.response.ShortFormUploadResponse;
import com.ott.api_admin.shortform.service.BackOfficeShortFormReader;
import com.ott.api_admin.shortform.service.BackOfficeShortFormService;
import com.ott.api_admin.shortform.service.BackOfficeShortFormWriter;
import com.ott.api_admin.upload.dto.request.MultipartUploadCompleteRequest;
import com.ott.api_admin.upload.dto.response.MultipartUploadPartUrlResponse;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.common.web.response.PageResponse;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.member.domain.Role;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class BackOfficeShortFormDelegationActorAuthorizationContractTest {

    private static final Long OWNER_ID = 11L;
    private static final Long MEDIA_ID = 101L;
    private static final Long SHORT_FORM_ID = 202L;
    private static final String OBJECT_KEY = "short-forms/202/origin.mp4";
    private static final String UPLOAD_ID = "upload-202";

    @Mock
    private BackOfficeShortFormReader reader;

    @Mock
    private BackOfficeShortFormWriter writer;

    @Mock
    private UploadHelper uploadHelper;

    @Mock
    private RabbitTranscodePublisher transcodePublisher;

    @Mock
    private BackOfficeShortFormService controllerService;

    @Test
    void serviceForwardsAdminActorToEveryShortformActorBoundary() {
        AdminActor actor = actor(OWNER_ID, Role.EDITOR);
        BackOfficeShortFormService service = new BackOfficeShortFormService(reader, writer, uploadHelper, transcodePublisher);
        ShortFormUploadRequest uploadRequest = uploadRequest();
        ShortFormUpdateRequest updateRequest = updateRequest();
        List<UploadHelper.MultipartPartETag> parts = List.of(new UploadHelper.MultipartPartETag(1, "etag-1"));
        when(reader.getShortFormUploadInfo(SHORT_FORM_ID, OBJECT_KEY, actor)).thenReturn(2);
        when(writer.createIngestJobWithOutbox(SHORT_FORM_ID, OBJECT_KEY))
                .thenReturn(new IngestJobResult(MEDIA_ID, 303L, OBJECT_KEY, 1000L, MediaType.SHORT_FORM));

        service.getShortFormList(0, 10, "clip", PublicStatus.PUBLIC, actor);
        service.getShortFormDetail(MEDIA_ID, actor);
        service.getShortFormOriginUploadPartUrls(SHORT_FORM_ID, OBJECT_KEY, UPLOAD_ID, 0, 2, actor);
        service.createShortFormUpload(uploadRequest, actor);
        service.updateShortFormUpload(SHORT_FORM_ID, updateRequest, actor);
        service.completeShortFormOriginUpload(SHORT_FORM_ID, OBJECT_KEY, UPLOAD_ID, parts, actor);

        verify(reader).getShortFormList(0, 10, "clip", PublicStatus.PUBLIC, actor);
        verify(reader).getShortFormDetail(MEDIA_ID, actor);
        verify(reader).getShortFormOriginUploadPartUrls(SHORT_FORM_ID, OBJECT_KEY, UPLOAD_ID, 0, 2, actor);
        verify(writer).createShortFormUpload(uploadRequest, actor);
        verify(writer).updateShortFormUpload(SHORT_FORM_ID, updateRequest, actor);
        InOrder order = inOrder(reader, uploadHelper, writer);
        order.verify(reader).getShortFormUploadInfo(SHORT_FORM_ID, OBJECT_KEY, actor);
        order.verify(uploadHelper).completeMultipartUpload(OBJECT_KEY, UPLOAD_ID, 2, parts);
        order.verify(writer).createIngestJobWithOutbox(SHORT_FORM_ID, OBJECT_KEY);
    }

    @Test
    void controllerForwardsAuthenticationToEveryShortformActorEndpoint() {
        Authentication authentication = authentication(OWNER_ID, Role.EDITOR);
        AdminActor actor = actor(OWNER_ID, Role.EDITOR);
        BackOfficeShortFormController controller = new BackOfficeShortFormController(controllerService);
        ShortFormUploadRequest uploadRequest = uploadRequest();
        ShortFormUpdateRequest updateRequest = updateRequest();
        PageResult<ShortFormListResponse> listPage = emptyPage();
        PageResult<MultipartUploadPartUrlResponse> partUrlPage = emptyPage();
        ShortFormDetailResponse detailResponse = detailResponse();
        ShortFormUploadResponse uploadResponse = uploadResponse();
        ShortFormUpdateResponse updateResponse = updateResponse();
        MultipartUploadCompleteRequest completeRequest = new MultipartUploadCompleteRequest(
                OBJECT_KEY,
                UPLOAD_ID,
                List.of(new MultipartUploadCompleteRequest.PartETagRequest(1, "etag-1")));
        when(controllerService.getShortFormDetail(MEDIA_ID, actor)).thenReturn(detailResponse);
        when(controllerService.createShortFormUpload(uploadRequest, actor)).thenReturn(uploadResponse);
        when(controllerService.updateShortFormUpload(SHORT_FORM_ID, updateRequest, actor))
                .thenReturn(updateResponse);
        when(controllerService.getShortFormList(0, 10, "clip", PublicStatus.PUBLIC, actor))
                .thenReturn(listPage);
        when(controllerService.getShortFormOriginUploadPartUrls(SHORT_FORM_ID, OBJECT_KEY, UPLOAD_ID, 0, 2, actor))
                .thenReturn(partUrlPage);

        PageResponse<ShortFormListResponse> listResponse = controller.getShortFormList(
                        0,
                        10,
                        "clip",
                        PublicStatus.PUBLIC,
                        authentication)
                .getBody()
                .getData();
        assertEmptyPage(listResponse);
        assertThat(controller.getShortFormDetail(MEDIA_ID, authentication).getBody().getData())
                .isSameAs(detailResponse);
        assertThat(controller.createShortFormUpload(uploadRequest, authentication).getBody().getData())
                .isSameAs(uploadResponse);
        assertThat(controller.updateShortFormUpload(SHORT_FORM_ID, updateRequest, authentication).getBody().getData())
                .isSameAs(updateResponse);
        controller.completeShortFormUpload(SHORT_FORM_ID, completeRequest, authentication);
        PageResponse<MultipartUploadPartUrlResponse> partUrlResponse = controller.getShortFormUploadPartUrls(
                        SHORT_FORM_ID,
                        OBJECT_KEY,
                        UPLOAD_ID,
                        0,
                        2,
                        authentication)
                .getBody()
                .getData();
        assertEmptyPage(partUrlResponse);

        verify(controllerService).getShortFormList(0, 10, "clip", PublicStatus.PUBLIC, actor);
        verify(controllerService).getShortFormDetail(MEDIA_ID, actor);
        verify(controllerService).createShortFormUpload(uploadRequest, actor);
        verify(controllerService).updateShortFormUpload(SHORT_FORM_ID, updateRequest, actor);
        verify(controllerService).completeShortFormOriginUpload(
                SHORT_FORM_ID,
                OBJECT_KEY,
                UPLOAD_ID,
                List.of(new UploadHelper.MultipartPartETag(1, "etag-1")),
                actor);
        verify(controllerService).getShortFormOriginUploadPartUrls(SHORT_FORM_ID, OBJECT_KEY, UPLOAD_ID, 0, 2, actor);
    }

    private static ShortFormUploadRequest uploadRequest() {
        return new ShortFormUploadRequest(
                303L,
                MediaType.SERIES,
                "short",
                "description",
                PublicStatus.PUBLIC,
                30,
                1200,
                "poster.jpg",
                "origin.mp4");
    }

    private static ShortFormUpdateRequest updateRequest() {
        return new ShortFormUpdateRequest(
                303L,
                MediaType.SERIES,
                "updated short",
                "updated description",
                PublicStatus.PUBLIC,
                "poster.jpg");
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
                null);
    }

    private static ShortFormUpdateResponse updateResponse() {
        return new ShortFormUpdateResponse(SHORT_FORM_ID, "poster-key", "thumbnail-key", "poster-url", "thumbnail-url");
    }

    private static ShortFormUploadResponse uploadResponse() {
        return new ShortFormUploadResponse(
                SHORT_FORM_ID,
                "poster-key",
                "thumbnail-key",
                OBJECT_KEY,
                "master-key",
                "poster-url",
                "thumbnail-url",
                UPLOAD_ID,
                2,
                1024L);
    }

    private static <T> PageResult<T> emptyPage() {
        return PageResult.of(PageMetadata.of(0, 0, 10), List.of());
    }

    private static <T> void assertEmptyPage(PageResponse<T> response) {
        assertThat(response.getPageInfo().getCurrentPage()).isZero();
        assertThat(response.getPageInfo().getTotalPage()).isZero();
        assertThat(response.getPageInfo().getPageSize()).isEqualTo(10);
        assertThat(response.getDataList()).isEmpty();
    }

    private static Authentication authentication(Long principal, Role role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role.getKey())));
    }

    private static AdminActor actor(Long memberId, Role role) {
        return new AdminActor(memberId, Set.of(role.getKey()));
    }
}
