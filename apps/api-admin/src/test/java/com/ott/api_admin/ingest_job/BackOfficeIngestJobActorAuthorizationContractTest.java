package com.ott.api_admin.ingest_job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.ingest_job.controller.BackOfficeIngestJobController;
import com.ott.api_admin.ingest_job.dto.response.IngestJobListResponse;
import com.ott.api_admin.ingest_job.mapper.BackOfficeIngestJobMapper;
import com.ott.api_admin.ingest_job.service.BackOfficeIngestJobService;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.common.web.response.PageResponse;
import com.ott.domain.member.domain.Role;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

@ExtendWith(MockitoExtension.class)
class BackOfficeIngestJobActorAuthorizationContractTest {

    private static final Long EDITOR_ID = 11L;
    private static final Long OTHER_ID = 22L;

    @Mock
    private BackOfficeIngestJobMapper mapper;

    @Mock
    private IngestJobRepository ingestJobRepository;

    @Mock
    private ContentsRepository contentsRepository;

    @Mock
    private ShortFormRepository shortFormRepository;

    @Mock
    private BackOfficeIngestJobService controllerService;

    @Test
    void getIngestJobListLeavesAdminUnscopedAndScopesEditorToPrincipal() {
        BackOfficeIngestJobService service = new BackOfficeIngestJobService(
                mapper,
                ingestJobRepository,
                contentsRepository,
                shortFormRepository);
        PageRequest firstPage = PageRequest.of(0, 10);
        when(ingestJobRepository.findIngestJobListWithMediaBySearchWordAndUploaderId(firstPage, "clip", null))
                .thenReturn(new PageImpl<>(List.of(), firstPage, 0));
        when(ingestJobRepository.findIngestJobListWithMediaBySearchWordAndUploaderId(firstPage, "clip", EDITOR_ID))
                .thenReturn(new PageImpl<>(List.of(), firstPage, 0));

        service.getIngestJobList(0, 10, "clip", actor(OTHER_ID, Role.ADMIN));
        service.getIngestJobList(0, 10, "clip", actor(EDITOR_ID, Role.EDITOR));

        verify(ingestJobRepository).findIngestJobListWithMediaBySearchWordAndUploaderId(firstPage, "clip", null);
        verify(ingestJobRepository).findIngestJobListWithMediaBySearchWordAndUploaderId(firstPage, "clip", EDITOR_ID);
    }

    @Test
    void controllerForwardsAuthenticationToIngestJobService() {
        Authentication authentication = authentication(EDITOR_ID, Role.EDITOR);
        AdminActor actor = actor(EDITOR_ID, Role.EDITOR);
        BackOfficeIngestJobController controller = new BackOfficeIngestJobController(controllerService);
        PageResult<IngestJobListResponse> expected = PageResult.of(PageMetadata.of(0, 0, 10), List.of());
        when(controllerService.getIngestJobList(0, 10, "clip", actor)).thenReturn(expected);

        PageResponse<IngestJobListResponse> response = controller.getIngestJobList(0, 10, "clip", authentication)
                .getBody()
                .getData();

        assertThat(response.getPageInfo().getCurrentPage()).isZero();
        assertThat(response.getPageInfo().getTotalPage()).isZero();
        assertThat(response.getPageInfo().getPageSize()).isEqualTo(10);
        assertThat(response.getDataList()).isEmpty();
        verify(controllerService).getIngestJobList(0, 10, "clip", actor);
    }

    private static AdminActor actor(Long memberId, Role role) {
        return new AdminActor(memberId, Set.of(role.getKey()));
    }

    private static Authentication authentication(Long principal, Role role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role.getKey())));
    }
}
