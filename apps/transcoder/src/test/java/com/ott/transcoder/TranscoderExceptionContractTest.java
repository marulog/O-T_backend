package com.ott.transcoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.MediaType;
import com.ott.infra.db.ingest_command.repository.IngestCommandRepository;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.infra.mq.TranscodeMessage;
import com.ott.infra.s3.service.S3PresignService;
import com.ott.transcoder.command.CommandExtractor;
import com.ott.transcoder.heartbeat.HeartbeatScheduler;
import com.ott.transcoder.inspection.DiskSpaceGuard;
import com.ott.transcoder.inspection.Inspector;
import com.ott.transcoder.inspection.probe.ProbeResult;
import com.ott.transcoder.job.IngestJobStatusManager;
import com.ott.transcoder.job.JobOrchestrator;
import com.ott.transcoder.pipeline.CommandPipelineExecutor;
import com.ott.transcoder.pipeline.hls.MasterPlaylistGenerator;
import com.ott.transcoder.queue.rabbit.DelayQueuePublisher;
import com.ott.transcoder.queue.rabbit.RabbitTranscodeListener;
import com.ott.transcoder.storage.VideoStorage;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class TranscoderExceptionContractTest {

    @Test
    void commandExtractorMissingJobKeepsCoreErrorCodeAndMessage() {
        IngestJobRepository ingestJobRepository = mock(IngestJobRepository.class);
        IngestCommandRepository ingestCommandRepository = mock(IngestCommandRepository.class);
        CommandExtractor extractor = new CommandExtractor(ingestJobRepository, ingestCommandRepository);
        TranscodeMessage message = new TranscodeMessage(
                10L,
                20L,
                "contents/10/origin/origin.mp4",
                1024L,
                MediaType.CONTENTS
        );

        when(ingestJobRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> extractor.extractCommand(message, probeResult()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INGEST_JOB_NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("IngestJob을 찾을 수 없습니다.");
                });
    }

    @Test
    void listenerMissingJobStopsBeforeEveryExternalProcessingBoundary() {
        IngestJobRepository ingestJobRepository = mock(IngestJobRepository.class);
        IngestCommandRepository ingestCommandRepository = mock(IngestCommandRepository.class);
        S3PresignService s3PresignService = mock(S3PresignService.class);
        IngestJobStatusManager statusManager = new IngestJobStatusManager(
                ingestJobRepository,
                ingestCommandRepository,
                s3PresignService
        );
        DiskSpaceGuard diskSpaceGuard = mock(DiskSpaceGuard.class);
        VideoStorage videoStorage = mock(VideoStorage.class);
        Inspector inspector = mock(Inspector.class);
        CommandExtractor commandExtractor = mock(CommandExtractor.class);
        CommandPipelineExecutor pipelineExecutor = mock(CommandPipelineExecutor.class);
        MasterPlaylistGenerator playlistGenerator = mock(MasterPlaylistGenerator.class);
        HeartbeatScheduler heartbeatScheduler = mock(HeartbeatScheduler.class);
        DelayQueuePublisher delayQueuePublisher = mock(DelayQueuePublisher.class);
        JobOrchestrator orchestrator = new JobOrchestrator(
                diskSpaceGuard,
                videoStorage,
                inspector,
                commandExtractor,
                pipelineExecutor,
                playlistGenerator,
                statusManager,
                heartbeatScheduler,
                delayQueuePublisher
        );
        RabbitTranscodeListener listener = new RabbitTranscodeListener(orchestrator, Optional.empty());
        TranscodeMessage message = new TranscodeMessage(
                10L,
                20L,
                "contents/10/origin/origin.mp4",
                1024L,
                MediaType.CONTENTS
        );

        when(ingestJobRepository.findById(20L)).thenReturn(Optional.empty());

        try (MockedConstruction<ProcessBuilder> externalProcesses = mockConstruction(ProcessBuilder.class)) {
            assertThatThrownBy(() -> listener.listen(message, false))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INGEST_JOB_NOT_FOUND);
                        assertThat(exception.getMessage()).isEqualTo("IngestJob을 찾을 수 없습니다.");
                    });

            verify(ingestJobRepository).findById(20L);
            verifyNoInteractions(
                    ingestCommandRepository,
                    s3PresignService,
                    diskSpaceGuard,
                    videoStorage,
                    inspector,
                    commandExtractor,
                    pipelineExecutor,
                    playlistGenerator,
                    heartbeatScheduler,
                    delayQueuePublisher
            );
            assertThat(externalProcesses.constructed()).isEmpty();
        }
    }

    private static ProbeResult probeResult() {
        return new ProbeResult(
                1920,
                1080,
                60.0,
                "h264",
                "aac",
                30.0,
                4_000_000,
                128_000,
                2,
                "yuv420p",
                0
        );
    }
}
