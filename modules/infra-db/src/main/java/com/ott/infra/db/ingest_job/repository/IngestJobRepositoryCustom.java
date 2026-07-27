package com.ott.infra.db.ingest_job.repository;

import com.ott.domain.ingest_job.domain.IngestJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IngestJobRepositoryCustom {

    Page<IngestJob> findIngestJobListWithMediaBySearchWordAndUploaderId(Pageable pageable, String searchWord, Long uploaderId);
}
