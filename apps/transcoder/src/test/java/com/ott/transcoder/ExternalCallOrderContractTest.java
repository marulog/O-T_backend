package com.ott.transcoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ott.domain.common.MediaType;
import com.ott.infra.mq.TranscodeMessage;
import com.ott.transcoder.command.CommandExtractor;
import com.ott.transcoder.heartbeat.Heartbeat;
import com.ott.transcoder.heartbeat.HeartbeatScheduler;
import com.ott.transcoder.inspection.DiskSpaceGuard;
import com.ott.transcoder.inspection.Inspector;
import com.ott.transcoder.inspection.probe.ProbeResult;
import com.ott.transcoder.job.IngestJobStatusManager;
import com.ott.transcoder.job.JobOrchestrator;
import com.ott.transcoder.pipeline.CommandPipelineExecutor;
import com.ott.transcoder.pipeline.hls.MasterPlaylistGenerator;
import com.ott.transcoder.queue.rabbit.DelayQueuePublisher;
import com.ott.transcoder.storage.VideoStorage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class ExternalCallOrderContractTest {

    @TempDir
    Path tempDir;

    @Test
    void handleClaimedJob_emitsBaselineExternalCallSequence() throws IOException {
        List<String> trace = new ArrayList<>();
        DiskSpaceGuard disk = mock(DiskSpaceGuard.class);
        VideoStorage storage = mock(VideoStorage.class);
        Inspector inspector = mock(Inspector.class);
        CommandExtractor commands = mock(CommandExtractor.class);
        CommandPipelineExecutor pipeline = mock(CommandPipelineExecutor.class);
        MasterPlaylistGenerator playlist = mock(MasterPlaylistGenerator.class);
        IngestJobStatusManager status = mock(IngestJobStatusManager.class);
        HeartbeatScheduler heartbeats = mock(HeartbeatScheduler.class);
        DelayQueuePublisher delayQueue = mock(DelayQueuePublisher.class);
        Heartbeat heartbeat = mock(Heartbeat.class);
        JobOrchestrator orchestrator = new JobOrchestrator(
                disk, storage, inspector, commands, pipeline, playlist, status, heartbeats, delayQueue);
        ReflectionTestUtils.setField(orchestrator, "tempDir", tempDir.toString());
        TranscodeMessage message = new TranscodeMessage(
                101L, 202L, "contents/101/origin/video.mp4", 4096L, MediaType.CONTENTS);
        Path input = tempDir.resolve("input.mp4");
        ProbeResult probe = probeResult();

        when(status.isTerminal(202L))
                .thenAnswer(invocation -> event(trace, "transcoder.job.handle -> status.isTerminal", false));
        when(status.startProcessing(202L))
                .thenAnswer(invocation -> event(trace, "transcoder.job.handle -> status.startProcessing", true));
        when(heartbeats.start(202L))
                .thenAnswer(invocation -> event(trace, "transcoder.job.handle -> heartbeat.start", heartbeat));
        doAnswer(invocation -> event(trace, "transcoder.job.handle -> diskSpaceGuard.check", null))
                .when(disk).check(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(4096L));
        when(storage.download(org.mockito.ArgumentMatchers.eq(message.originUrl()), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> event(trace, "transcoder.job.handle -> videoStorage.download", input));
        when(inspector.inspect(input))
                .thenAnswer(invocation -> event(trace, "transcoder.job.handle -> inspector.inspect", probe));
        when(commands.extractCommand(message, probe))
                .thenAnswer(invocation -> event(
                        trace, "transcoder.job.handle -> commandExtractor.extractCommand", List.of()));
        when(status.getCompletedResolutions(202L))
                .thenAnswer(invocation -> event(
                        trace, "transcoder.job.handle -> status.getCompletedResolutions", List.of()));
        doAnswer(invocation -> event(trace, "transcoder.job.handle -> status.checkAllCompleted", null))
                .when(status).checkAllCompleted(202L);
        doAnswer(invocation -> event(trace, "transcoder.job.handle -> heartbeat.close", null))
                .when(heartbeat).close();

        orchestrator.handle(message, false);

        assertTrace("contracts/external-call-order/transcoder-job.txt", trace);
    }

    private static <T> T event(List<String> trace, String name, T result) {
        trace.add(name);
        return result;
    }

    private static ProbeResult probeResult() {
        return new ProbeResult(
                1920, 1080, 60.0, "h264", "aac", 30.0, 4_000_000, 128_000, 2, "yuv420p", 0);
    }

    private static void assertTrace(String resource, List<String> trace) throws IOException {
        InputStream stream = ExternalCallOrderContractTest.class.getClassLoader().getResourceAsStream(resource);
        assertThat(stream).as("external call order fixture %s", resource).isNotNull();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            assertThat(trace).containsExactlyElementsOf(
                    reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList());
        }
        trace.forEach(line -> System.out.println("EXTERNAL_CALL_ORDER_TRACE " + line));
    }
}
