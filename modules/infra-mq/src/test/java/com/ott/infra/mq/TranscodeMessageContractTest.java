package com.ott.infra.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.domain.common.MediaType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscodeMessageContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCurrentFieldNamesInRecordOrder() throws Exception {
        TranscodeMessage message = new TranscodeMessage(
                10L,
                20L,
                "contents/10/origin/origin.mp4",
                3072L,
                MediaType.CONTENTS
        );

        String json = objectMapper.writeValueAsString(message);
        JsonNode root = objectMapper.readTree(json);
        List<String> fieldNames = new ArrayList<>();
        root.fieldNames().forEachRemaining(fieldNames::add);

        assertThat(fieldNames).containsExactly("mediaId", "ingestJobId", "originUrl", "fileSize", "mediaType");
        assertThat(json).isEqualTo("""
                {"mediaId":10,"ingestJobId":20,"originUrl":"contents/10/origin/origin.mp4","fileSize":3072,"mediaType":"CONTENTS"}""");
    }
}
