package com.ott.transcoder.pipeline.thumbnail;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.ingest_command.domain.CommandType;
import com.ott.transcoder.command.Command;
import com.ott.transcoder.command.ThumbnailCommand;
import com.ott.transcoder.job.JobContext;
import com.ott.transcoder.pipeline.CommandPipeline;
import com.ott.transcoder.storage.VideoStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor
@Component
public class ThumbnailCommandPipeline implements CommandPipeline<ThumbnailCommand> {

    private final ThumbnailExtractor thumbnailExtractor;
    private final VideoStorage videoStorage;
    private final ContentsRepository contentsRepository;

    @Override
    public boolean support(Command command) {
        return CommandType.THUMBNAIL.equals(command.getType());
    }

    @Override
    public String execute(ThumbnailCommand command, JobContext jobContext) {
        log.info("썸네일 추출 시작 - mediaId: {}", jobContext.mediaId());

        // 1. mediaId → contentsId 조회
        Contents contents = contentsRepository.findByMediaId(jobContext.mediaId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENTS_NOT_FOUND));

        // 2. 프레임 추출 (밝기 검증 포함)
        Path thumbnailFile = thumbnailExtractor.extract(
                jobContext.inputFile(), jobContext.outputDir(), jobContext.probeResult()
        );

        // 3. 기존 업로드 경로 패턴에 맞춰 S3 key 생성
        String objectKey = "contents/" + contents.getId() + "/thumbnail/thumbnail.jpg";
        videoStorage.putFile(thumbnailFile, objectKey);

        log.info("썸네일 업로드 완료 - mediaId: {}, contentsId: {}, objectKey: {}",
                jobContext.mediaId(), contents.getId(), objectKey);

        return objectKey;
    }
}