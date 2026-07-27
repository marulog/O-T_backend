package com.ott.api_admin.ingest_job.service;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.ingest_job.dto.response.IngestJobListResponse;
import com.ott.api_admin.ingest_job.mapper.BackOfficeIngestJobMapper;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.common.MediaType;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.ingest_job.domain.IngestJob;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.domain.member.domain.Role;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BackOfficeIngestJobService {

    private final BackOfficeIngestJobMapper backOfficeIngestJobMapper;

    private final IngestJobRepository ingestJobRepository;
    private final ContentsRepository contentsRepository;
    private final ShortFormRepository shortFormRepository;

    @Transactional(readOnly = true)
    public PageResult<IngestJobListResponse> getIngestJobList(
            Integer page, Integer size, String searchWord, AdminActor actor
    ) {
        Pageable pageable = PageRequest.of(page, size);

        // 1. 관리자/에디터 여부 확인
        Long memberId = actor.memberId();
        boolean isEditor = actor.roleKeys().contains(Role.EDITOR.getKey());
        Long uploaderId = null;

        // 2. 에디터인 경우 본인이 업로드한 작업만 조회 가능
        if (isEditor)
            uploaderId = memberId;

        // 2. IngestJob 페이징 조회 (Media + Uploader fetchJoin)
        Page<IngestJob> ingestJobPage = ingestJobRepository.findIngestJobListWithMediaBySearchWordAndUploaderId(
                pageable, searchWord, uploaderId
        );

        List<IngestJob> ingestJobList = ingestJobPage.getContent();

        // 3. 타입별로  mediaId 분리
        List<Long> contentsMediaIdList = ingestJobList.stream()
                .filter(j -> j.getMedia().getMediaType() == MediaType.CONTENTS)
                .map(j -> j.getMedia().getId())
                .toList();

        List<Long> shortFormMediaIdList = ingestJobList.stream()
                .filter(j -> j.getMedia().getMediaType() == MediaType.SHORT_FORM)
                .map(j -> j.getMedia().getId())
                .toList();

        // 4. 일괄 조회: mediaId → videoSize 매핑
        Map<Long, Integer> videoSizeByMediaId = new HashMap<>();

        // 5. 빈 리스트면 조회 x
        if (!contentsMediaIdList.isEmpty()) {
            contentsRepository.findAllByMediaIdIn(contentsMediaIdList).forEach(
                    c -> videoSizeByMediaId.put(c.getMedia().getId(), c.getVideoSize())
            );
        }

        if (!shortFormMediaIdList.isEmpty()) {
            shortFormRepository.findAllByMediaIdIn(shortFormMediaIdList).forEach(
                    s -> videoSizeByMediaId.put(s.getMedia().getId(), s.getVideoSize())
            );
        }

        List<IngestJobListResponse> responseList = ingestJobList.stream()
                .map(j -> backOfficeIngestJobMapper.toIngestJobListResponse(j, videoSizeByMediaId))
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(
                ingestJobPage.getNumber(),
                ingestJobPage.getTotalPages(),
                ingestJobPage.getSize()
        );

        return PageResult.of(pageMetadata, responseList);
    }
}
