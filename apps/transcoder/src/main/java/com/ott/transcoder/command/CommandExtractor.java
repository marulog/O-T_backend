package com.ott.transcoder.command;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.MediaType;
import com.ott.domain.ingest_command.domain.CommandStatus;
import com.ott.domain.ingest_command.domain.IngestCommand;
import com.ott.infra.db.ingest_command.repository.IngestCommandRepository;
import com.ott.domain.ingest_job.domain.IngestJob;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.transcoder.ffmpeg.Resolution;
import com.ott.transcoder.inspection.probe.ProbeResult;
import com.ott.infra.mq.TranscodeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class CommandExtractor {

    private final IngestJobRepository ingestJobRepository;
    private final IngestCommandRepository ingestCommandRepository;

    @Transactional
    public List<Command> extractCommand(TranscodeMessage message, ProbeResult probeResult) {
        Long ingestJobId = message.ingestJobId();
        IngestJob ingestJob = ingestJobRepository.findById(ingestJobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INGEST_JOB_NOT_FOUND));

        // 1. 후보 커맨드 추출
        List<Command> candidateList = buildCandidateList(message, probeResult, ingestJob);

        // 2. 기존 커맨드 한 번에 조회
        List<IngestCommand> existingCommandList = ingestCommandRepository
                .findByIngestJobId(ingestJobId);

        Set<String> completedKeySet = existingCommandList.stream()
                .filter(cmd -> cmd.getCommandStatus() == CommandStatus.COMPLETED)
                .map(IngestCommand::getCommandKey)
                .collect(Collectors.toSet());

        Set<String> existingKeySet = existingCommandList.stream()
                .map(IngestCommand::getCommandKey)
                .collect(Collectors.toSet());

        // 3. 완료 제외 = 실행 대상
        List<Command> pendingList = candidateList.stream()
                .filter(cmd -> !completedKeySet.contains(cmd.getCommandKey()))
                .toList();

        List<IngestCommand> newCommandList = pendingList.stream()
                .filter(cmd -> !existingKeySet.contains(cmd.getCommandKey()))
                .map(cmd -> IngestCommand.builder()
                        .ingestJob(ingestJob)
                        .commandType(cmd.getType())
                        .commandKey(cmd.getCommandKey())
                        .commandStatus(CommandStatus.PENDING)
                        .build())
                .toList();

        ingestCommandRepository.saveAll(newCommandList); // TODO: 많을 경우 Bulk INSERT

        log.info("커맨드 추출 - ingestJobId: {}, candidate: {}, completed: {}, pending: {}, newSaved: {}",
                ingestJobId, candidateList.size(), completedKeySet.size(),
                pendingList.size(), newCommandList.size());

        return pendingList;
    }

    private List<Command> buildCandidateList(TranscodeMessage message, ProbeResult probeResult, IngestJob ingestJob) {
        List<Command> list = new ArrayList<>();
        for (Resolution resolution : Resolution.values()) {
            if (!probeResult.isUpscaleFor(resolution.getHeight())) {
                list.add(new TranscodeCommand(resolution));
            }
        }
        if (list.isEmpty()) {
            log.warn("모든 프로필 업스케일 → 360p fallback - mediaId: {}", message.mediaId());
            list.add(new TranscodeCommand(Resolution.P360));
        }

         // 숏폼일 경우 커맨드 추출 제외 -> 숏폼은 썸네일 없음 && null인 경우
        if (message.mediaType() != MediaType.SHORT_FORM && ingestJob.getMedia().getThumbnailUrl() == null) {
            list.add(new ThumbnailCommand());
        }

        return list;
    }
}
