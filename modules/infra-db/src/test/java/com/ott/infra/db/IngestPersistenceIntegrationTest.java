package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.ingest_command.domain.CommandStatus;
import com.ott.domain.ingest_command.domain.CommandType;
import com.ott.domain.ingest_command.domain.IngestCommand;
import com.ott.domain.ingest_job.domain.IngestJob;
import com.ott.domain.ingest_job.domain.IngestStatus;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.outbox.domain.OutboxStatus;
import com.ott.domain.outbox.domain.TranscodeOutbox;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.ingest_command.repository.IngestCommandRepository;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.outbox.repository.TranscodeOutboxRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = InfraDbConfiguration.class)
class IngestPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private IngestJobRepository ingestJobRepository;

    @Autowired
    private IngestCommandRepository ingestCommandRepository;

    @Autowired
    private TranscodeOutboxRepository outboxRepository;

    @Test
    void ingestQueriesPreemptionAndOutboxPollingKeepTheirContracts() {
        Member uploader = memberRepository.saveAndFlush(member("ingest-owner"));
        Media media = persistMedia(uploader, "Task 15 ingest title");
        IngestJob job = ingestJobRepository.saveAndFlush(job(media, IngestStatus.PENDING));
        ingestCommandRepository.saveAndFlush(command(job, "360p", CommandStatus.PENDING));
        ingestCommandRepository.saveAndFlush(command(job, "thumbnail", CommandStatus.COMPLETED));
        TranscodeOutbox first = outboxRepository.saveAndFlush(outbox(media, job, "s3://source/first.mp4"));
        TranscodeOutbox second = outboxRepository.saveAndFlush(outbox(media, job, "s3://source/second.mp4"));
        entityManager.clear();

        int claimed = ingestJobRepository.tryPreempt(job.getId(), 60);
        Page<IngestJob> searchResult = ingestJobRepository
                .findIngestJobListWithMediaBySearchWordAndUploaderId(
                        PageRequest.of(0, 10), "Task 15", uploader.getId());
        List<TranscodeOutbox> pending = outboxRepository
                .findTop50ByOutboxStatusOrderByCreatedDateAsc(OutboxStatus.PENDING);

        assertThat(claimed).isEqualTo(1);
        assertThat(ingestJobRepository.findById(job.getId()).orElseThrow().getIngestStatus())
                .isEqualTo(IngestStatus.PROCESSING);
        assertThat(ingestJobRepository.findById(job.getId()).orElseThrow().getHeartbeatAt()).isNotNull();
        assertThat(searchResult.getContent()).extracting(IngestJob::getId).containsExactly(job.getId());
        assertThat(ingestCommandRepository.findByIngestJobId(job.getId())).hasSize(2);
        assertThat(pending).extracting(TranscodeOutbox::getId)
                .containsSubsequence(first.getId(), second.getId());
    }

    @Test
    void duplicateCommandIsRejectedWhileDuplicateOutboxPayloadRemainsAllowed() {
        Member uploader = memberRepository.saveAndFlush(member("duplicate-owner"));
        Media media = persistMedia(uploader, "Duplicate contract");
        IngestJob job = ingestJobRepository.saveAndFlush(job(media, IngestStatus.PENDING));
        ingestCommandRepository.saveAndFlush(command(job, "same-key", CommandStatus.PENDING));

        assertThatThrownBy(() -> ingestCommandRepository.saveAndFlush(
                command(job, "same-key", CommandStatus.COMPLETED)))
                .isInstanceOf(DataIntegrityViolationException.class);

        entityManager.clear();
        outboxRepository.saveAndFlush(outbox(media, job, "s3://source/duplicate.mp4"));
        outboxRepository.saveAndFlush(outbox(media, job, "s3://source/duplicate.mp4"));
        assertThat(outboxRepository.findTop50ByOutboxStatusOrderByCreatedDateAsc(OutboxStatus.PENDING))
                .hasSize(2);
    }

    @Test
    void completedJobCannotBePreemptedAndOutboxRetryStateIsPreserved() {
        Member uploader = memberRepository.saveAndFlush(member("state-owner"));
        Media media = persistMedia(uploader, "State contract");
        IngestJob job = ingestJobRepository.saveAndFlush(job(media, IngestStatus.SUCCESS));
        TranscodeOutbox outbox = outboxRepository.saveAndFlush(outbox(media, job, "s3://source/retry.mp4"));

        int claimed = ingestJobRepository.tryPreempt(job.getId(), 60);
        TranscodeOutbox managedOutbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        managedOutbox.markFailed("first failure");
        managedOutbox.markFailed("second failure");
        outboxRepository.flush();
        entityManager.clear();

        TranscodeOutbox persisted = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(claimed).isZero();
        assertThat(ingestJobRepository.findById(job.getId()).orElseThrow().getIngestStatus())
                .isEqualTo(IngestStatus.SUCCESS);
        assertThat(persisted.getOutboxStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(persisted.getRetryCount()).isEqualTo(2);
        assertThat(persisted.getErrorMessage()).isEqualTo("second failure");
    }

    private Media persistMedia(Member uploader, String title) {
        Media media = Media.builder()
                .uploader(uploader)
                .title(title)
                .description("Task 15 persistence integration sample")
                .posterUrl("https://cdn.example.com/poster.jpg")
                .thumbnailUrl("https://cdn.example.com/thumb.jpg")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(MediaType.CONTENTS)
                .publicStatus(PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
        entityManager.persist(media);
        entityManager.flush();
        return media;
    }

    private static Member member(String providerId) {
        return Member.builder()
                .provider(Provider.KAKAO)
                .providerId(providerId)
                .email(providerId + "@example.com")
                .nickname(providerId)
                .role(Role.EDITOR)
                .build();
    }

    private static IngestJob job(Media media, IngestStatus status) {
        return IngestJob.builder()
                .media(media)
                .ingestStatus(status)
                .build();
    }

    private static IngestCommand command(IngestJob job, String key, CommandStatus status) {
        return IngestCommand.builder()
                .ingestJob(job)
                .commandType(CommandType.TRANSCODE)
                .commandKey(key)
                .commandStatus(status)
                .build();
    }

    private static TranscodeOutbox outbox(Media media, IngestJob job, String originUrl) {
        return TranscodeOutbox.builder()
                .mediaId(media.getId())
                .ingestJobId(job.getId())
                .originUrl(originUrl)
                .fileSize(1024L)
                .mediaType(MediaType.CONTENTS)
                .maxRetries(2)
                .build();
    }
}
