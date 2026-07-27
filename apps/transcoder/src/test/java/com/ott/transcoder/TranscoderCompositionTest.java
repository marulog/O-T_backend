package com.ott.transcoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.MediaType;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.infra.mq.InfraMqConfiguration;
import com.ott.infra.mq.TranscodeMessage;
import com.ott.infra.s3.config.InfraS3Configuration;
import com.ott.transcoder.ffmpeg.execution.FfmpegExecutor;
import com.ott.transcoder.ffmpeg.execution.processbuilder.ProcessBuilderFfmpegExecutor;
import com.ott.transcoder.inspection.DiskSpaceGuard;
import com.ott.transcoder.inspection.Inspector;
import com.ott.transcoder.inspection.probe.execution.FfprobeExecutor;
import com.ott.transcoder.inspection.probe.execution.processbuilder.ProcessBuilderFfprobeExecutor;
import com.ott.transcoder.pipeline.CommandPipelineExecutor;
import com.ott.transcoder.queue.rabbit.RabbitDeadLetterListener;
import com.ott.transcoder.queue.rabbit.RabbitTranscodeListener;
import com.ott.transcoder.storage.LocalVideoStorage;
import com.ott.transcoder.storage.VideoStorage;
import java.util.Arrays;
import java.util.Objects;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "transcoder.messaging.provider=rabbit",
        "transcoder.ffmpeg.engine=processbuilder",
        "transcoder.ffprobe.engine=processbuilder"
})
class TranscoderCompositionTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("transcoder_context")
            .withUsername("ott")
            .withPassword("ottpw");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private RabbitTranscodeListener listener;

    @SpyBean
    private DiskSpaceGuard diskSpaceGuard;

    @SpyBean
    private VideoStorage videoStorage;

    @SpyBean
    private Inspector inspector;

    @SpyBean
    private CommandPipelineExecutor commandPipelineExecutor;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Test
    void applicationImportsOnlyPublishedInfraCompositions() {
        Import importedConfigurations = TranscoderApplication.class.getAnnotation(Import.class);

        assertThat(importedConfigurations).isNotNull();
        assertThat(Arrays.asList(importedConfigurations.value()))
                .containsExactlyInAnyOrder(
                        InfraDbConfiguration.class,
                        InfraS3Configuration.class,
                        InfraMqConfiguration.class
                );
    }

    @Test
    void contextContainsEachWorkerBoundaryExactlyOnce() {
        assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(context.getBeansOfType(RabbitTranscodeListener.class)).hasSize(1);
        assertThat(context.getBeansOfType(RabbitDeadLetterListener.class)).hasSize(1);
        assertThat(context.getBeansOfType(VideoStorage.class).values())
                .singleElement().isInstanceOf(LocalVideoStorage.class);
        assertThat(context.getBeansOfType(FfmpegExecutor.class).values())
                .singleElement().isInstanceOf(ProcessBuilderFfmpegExecutor.class);
        assertThat(context.getBeansOfType(FfprobeExecutor.class).values())
                .singleElement().isInstanceOf(ProcessBuilderFfprobeExecutor.class);
        assertThat(context.getBeansOfType(IngestJobRepository.class)).hasSize(1);

        assertThat(listenerRegistry.getListenerContainers()).hasSize(2);
        assertThat(listenerRegistry.getListenerContainer("transcode-consumer")).isNotNull();
        assertThat(listenerRegistry.getListenerContainers()).allMatch(container -> !container.isRunning());
    }

    @Test
    void contextContainsNoApiOrWebAdviceBeans() {
        assertThat(Arrays.stream(context.getBeanDefinitionNames())
                .map(context::getType)
                .filter(Objects::nonNull)
                .map(Class::getPackageName)
                .filter(packageName -> packageName.startsWith("com.ott.common.web")
                        || packageName.startsWith("com.ott.api_user")
                        || packageName.startsWith("com.ott.api_admin")))
                .isEmpty();
    }

    @Test
    void actualListenerRejectsMissingJobBeforeExternalProcessing() {
        TranscodeMessage message = new TranscodeMessage(
                9_999_991L,
                9_999_992L,
                "contents/9999991/origin/origin.mp4",
                1024L,
                MediaType.CONTENTS
        );
        Path workDir = Path.of(
                System.getProperty("java.io.tmpdir"),
                "ott-transcode-test",
                "media-9999991",
                "job-9999992"
        );
        clearInvocations(diskSpaceGuard, videoStorage, inspector, commandPipelineExecutor);

        try (var externalProcesses = org.mockito.Mockito.mockConstruction(ProcessBuilder.class)) {
            assertThatThrownBy(() -> listener.listen(message, false))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INGEST_JOB_NOT_FOUND);
                        assertThat(exception.getMessage()).isEqualTo("IngestJob을 찾을 수 없습니다.");
                    });

            assertThat(externalProcesses.constructed()).isEmpty();
        }

        verifyNoInteractions(diskSpaceGuard, videoStorage, inspector, commandPipelineExecutor);
        assertThat(workDir).doesNotExist();
    }
}
