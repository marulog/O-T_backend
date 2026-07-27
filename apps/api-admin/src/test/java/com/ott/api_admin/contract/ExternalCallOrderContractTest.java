package com.ott.api_admin.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.content.service.BackOfficeContentsReader;
import com.ott.api_admin.content.service.BackOfficeContentsService;
import com.ott.api_admin.content.service.BackOfficeContentsWriter;
import com.ott.api_admin.content.vo.IngestJobResult;
import com.ott.api_admin.outbox.poller.OutboxPoller;
import com.ott.api_admin.outbox.writer.OutboxWriter;
import com.ott.api_admin.publish.RabbitTranscodePublisher;
import com.ott.api_admin.shortform.service.BackOfficeShortFormReader;
import com.ott.api_admin.shortform.service.BackOfficeShortFormService;
import com.ott.api_admin.shortform.service.BackOfficeShortFormWriter;
import com.ott.api_admin.upload.support.UploadHelper;
import com.ott.domain.common.MediaType;
import com.ott.domain.member.domain.Role;
import com.ott.domain.outbox.domain.OutboxStatus;
import com.ott.domain.outbox.domain.TranscodeOutbox;
import com.ott.infra.db.outbox.repository.TranscodeOutboxRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExternalCallOrderContractTest {

    @Test
    void completeContentsOriginUpload_emitsBaselineExternalCallSequence() throws IOException {
        List<String> trace = new ArrayList<>();
        BackOfficeContentsReader reader = mock(BackOfficeContentsReader.class);
        BackOfficeContentsWriter writer = mock(BackOfficeContentsWriter.class);
        UploadHelper upload = mock(UploadHelper.class);
        RabbitTranscodePublisher publisher = mock(RabbitTranscodePublisher.class);
        String objectKey = "contents/23/origin.mp4";
        UploadHelper.MultipartPartETag part = new UploadHelper.MultipartPartETag(1, "etag-1");

        when(reader.getContentsUploadInfo(23L, objectKey))
                .thenAnswer(invocation -> event(trace, "api-admin.contents.complete -> reader.getContentsUploadInfo", 4));
        doAnswer(invocation -> event(trace, "api-admin.contents.complete -> uploadHelper.completeMultipartUpload", null))
                .when(upload).completeMultipartUpload(objectKey, "upload-abc", 4, List.of(part));
        when(writer.createIngestJobWithOutbox(23L, objectKey))
                .thenAnswer(invocation -> event(
                        trace,
                        "api-admin.contents.complete -> writer.createIngestJobWithOutbox",
                        new IngestJobResult(101L, 202L, objectKey, 4000L, MediaType.CONTENTS)));
        doAnswer(invocation -> event(trace, "api-admin.contents.complete -> transcodePublisher.publish", null))
                .when(publisher).publish(org.mockito.ArgumentMatchers.any());

        new BackOfficeContentsService(reader, writer, upload, publisher)
                .completeContentsOriginUpload(23L, objectKey, "upload-abc", List.of(part));

        assertTrace("contracts/external-call-order/api-admin-contents.txt", trace);
    }

    @Test
    void completeShortFormOriginUpload_emitsBaselineExternalCallSequence() throws IOException {
        List<String> trace = new ArrayList<>();
        BackOfficeShortFormReader reader = mock(BackOfficeShortFormReader.class);
        BackOfficeShortFormWriter writer = mock(BackOfficeShortFormWriter.class);
        UploadHelper upload = mock(UploadHelper.class);
        RabbitTranscodePublisher publisher = mock(RabbitTranscodePublisher.class);
        AdminActor actor = new AdminActor(55L, Set.of(Role.EDITOR.getKey()));
        String objectKey = "short-forms/14/origin.mp4";
        UploadHelper.MultipartPartETag part = new UploadHelper.MultipartPartETag(1, "etag-x");

        when(reader.getShortFormUploadInfo(14L, objectKey, actor))
                .thenAnswer(invocation -> event(
                        trace, "api-admin.short-form.complete -> reader.getShortFormUploadInfo", 5));
        doAnswer(invocation -> event(
                trace, "api-admin.short-form.complete -> uploadHelper.completeMultipartUpload", null))
                .when(upload).completeMultipartUpload(objectKey, "upload-xyz", 5, List.of(part));
        when(writer.createIngestJobWithOutbox(14L, objectKey))
                .thenAnswer(invocation -> event(
                        trace,
                        "api-admin.short-form.complete -> writer.createIngestJobWithOutbox",
                        new IngestJobResult(33L, 44L, objectKey, 5000L, MediaType.SHORT_FORM)));
        doAnswer(invocation -> event(trace, "api-admin.short-form.complete -> transcodePublisher.publish", null))
                .when(publisher).publish(org.mockito.ArgumentMatchers.any());

        new BackOfficeShortFormService(reader, writer, upload, publisher)
                .completeShortFormOriginUpload(14L, objectKey, "upload-xyz", List.of(part), actor);

        assertTrace("contracts/external-call-order/api-admin-short-form.txt", trace);
    }

    @Test
    void pollAndPublish_emitsBaselineExternalCallSequence() throws IOException {
        List<String> trace = new ArrayList<>();
        TranscodeOutboxRepository repository = mock(TranscodeOutboxRepository.class);
        RabbitTranscodePublisher publisher = mock(RabbitTranscodePublisher.class);
        OutboxWriter writer = mock(OutboxWriter.class);
        TranscodeOutbox outbox = outbox();

        when(repository.findTop50ByOutboxStatusOrderByCreatedDateAsc(OutboxStatus.PENDING))
                .thenAnswer(invocation -> event(
                        trace, "api-admin.outbox.poll -> outboxRepository.findPending", List.of(outbox)));
        doAnswer(invocation -> event(trace, "api-admin.outbox.poll -> rabbitTranscodePublisher.publish", null))
                .when(publisher).publish(org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> event(trace, "api-admin.outbox.poll -> outboxWriter.markAsPublished", null))
                .when(writer).markAsPublished(77L);

        new OutboxPoller(repository, publisher, writer).pollAndPublish();

        assertTrace("contracts/external-call-order/api-admin-outbox.txt", trace);
    }

    private static TranscodeOutbox outbox() {
        TranscodeOutbox outbox = mock(TranscodeOutbox.class);
        when(outbox.getId()).thenReturn(77L);
        when(outbox.getMediaId()).thenReturn(101L);
        when(outbox.getIngestJobId()).thenReturn(202L);
        when(outbox.getOriginUrl()).thenReturn("contents/101/origin/video.mp4");
        when(outbox.getFileSize()).thenReturn(4096L);
        when(outbox.getMediaType()).thenReturn(MediaType.CONTENTS);
        return outbox;
    }

    private static <T> T event(List<String> trace, String name, T result) {
        trace.add(name);
        return result;
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
