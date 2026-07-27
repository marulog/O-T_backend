package com.ott.transcoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import com.ott.transcoder.config.RabbitConsumerConfig;
import com.ott.transcoder.config.TranscodeFatalExceptionStrategy;
import com.ott.transcoder.exception.TranscodeErrorCode;
import com.ott.transcoder.exception.fatal.InvalidInputException;
import com.ott.transcoder.exception.retryable.StorageException;
import com.ott.transcoder.ffmpeg.Resolution;
import com.ott.transcoder.ffmpeg.TranscodeProfile;
import com.ott.transcoder.ffmpeg.execution.processbuilder.ProcessBuilderFfmpegExecutor;
import com.ott.transcoder.queue.rabbit.RabbitDeadLetterListener;
import com.ott.transcoder.queue.rabbit.RabbitTranscodeListener;
import com.ott.transcoder.storage.LocalVideoStorage;
import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.test.util.ReflectionTestUtils;

class TranscoderBaselineContractTest {

    @TempDir
    Path tempDir;

    @Test
    void rabbitListenerIdsQueuesAndFailureClassificationRemainStable() throws Exception {
        Method mainListener = RabbitTranscodeListener.class.getMethod(
                "listen",
                com.ott.infra.mq.TranscodeMessage.class,
                boolean.class
        );
        RabbitListener mainAnnotation = mainListener.getAnnotation(RabbitListener.class);
        Method deadLetterListener = RabbitDeadLetterListener.class.getMethod(
                "handleDeadLetter",
                com.ott.infra.mq.TranscodeMessage.class
        );
        RabbitListener deadLetterAnnotation = deadLetterListener.getAnnotation(RabbitListener.class);

        assertThat(RabbitConsumerConfig.LISTENER_ID).isEqualTo("transcode-consumer");
        assertThat(RabbitConsumerConfig.QUEUE_NAME).isEqualTo("transcode.queue");
        assertThat(RabbitConsumerConfig.DEAD_LETTER_QUEUE).isEqualTo("transcode.dead.queue");
        assertThat(mainAnnotation.id()).isEqualTo(RabbitConsumerConfig.LISTENER_ID);
        assertThat(mainAnnotation.queues()).containsExactly(RabbitConsumerConfig.QUEUE_NAME);
        assertThat(deadLetterAnnotation.queues()).containsExactly(RabbitConsumerConfig.DEAD_LETTER_QUEUE);

        TranscodeFatalExceptionStrategy strategy = new TranscodeFatalExceptionStrategy();
        assertThat(strategy.isFatal(new RuntimeException(
                new InvalidInputException(TranscodeErrorCode.INVALID_FILE_FORMAT, "invalid")
        ))).isTrue();
        assertThat(strategy.isFatal(new RuntimeException(
                new StorageException(TranscodeErrorCode.STORAGE_FAILED, "retry")
        ))).isFalse();
    }

    @Test
    void ffmpegCommandArgumentsRemainStable() throws Exception {
        ProcessBuilderFfmpegExecutor executor = new ProcessBuilderFfmpegExecutor();
        ReflectionTestUtils.setField(executor, "ffmpegPath", "dummy-ffmpeg");
        ReflectionTestUtils.setField(executor, "segmentDuration", 10);
        Path input = tempDir.resolve("input.mp4");
        Path output = tempDir.resolve("output");
        TranscodeProfile profile = TranscodeProfile.defaultFor(Resolution.P720);

        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        AtomicReference<Object> capturedCommand = new AtomicReference<>();

        try (MockedConstruction<ProcessBuilder> construction = mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    capturedCommand.set(context.arguments().getFirst());
                    when(mock.start()).thenReturn(process);
                }
        )) {
            Path playlist = executor.execute(input, output, profile);

            assertThat(playlist).isEqualTo(output.resolve("720p/media.m3u8"));
            assertThat(construction.constructed()).hasSize(1);
            assertThat(capturedCommand.get())
                    .isEqualTo(List.of(
                            "dummy-ffmpeg", "-i", input.toString(),
                            "-threads", "3",
                            "-vf", "scale=-2:720",
                            "-c:v", "libx264", "-preset", "fast",
                            "-c:a", "aac", "-b:a", "128k",
                            "-b:v", "2400k",
                            "-f", "hls",
                            "-hls_time", "10",
                            "-hls_list_size", "0",
                            "-hls_segment_filename", output.resolve("720p/segment_%03d.ts").toString(),
                            output.resolve("720p/media.m3u8").toString()
                    ));
        }
    }

    @Test
    void localStoragePreservesDestinationKeys() throws Exception {
        LocalVideoStorage storage = new LocalVideoStorage();
        Path storageRoot = tempDir.resolve("storage");
        ReflectionTestUtils.setField(storage, "outputDir", storageRoot.toString());
        Path source = tempDir.resolve("source.mp4");
        Files.writeString(source, "video", StandardCharsets.UTF_8);
        Path workDir = Files.createDirectories(tempDir.resolve("work"));

        assertThat(storage.download(source.toString(), workDir))
                .hasSameTextualContentAs(source);

        Path uploadDir = Files.createDirectories(tempDir.resolve("upload"));
        Files.writeString(uploadDir.resolve("master.m3u8"), "playlist", StandardCharsets.UTF_8);
        assertThat(storage.upload(uploadDir, "contents/10/hls"))
                .isEqualTo(storageRoot.resolve("contents/10/hls").toString());
        assertThat(storageRoot.resolve("contents/10/hls/master.m3u8"))
                .hasContent("playlist");

        Path thumbnail = tempDir.resolve("thumbnail.jpg");
        Files.write(thumbnail, List.of("image"), StandardCharsets.UTF_8);
        storage.putFile(thumbnail, "contents/10/thumbnail/thumbnail.jpg");
        assertThat(storageRoot.resolve("contents/10/thumbnail/thumbnail.jpg"))
                .hasSameTextualContentAs(thumbnail);
    }
}
