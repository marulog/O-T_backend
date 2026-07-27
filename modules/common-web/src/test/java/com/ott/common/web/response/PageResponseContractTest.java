package com.ott.common.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void pageResponseJsonContractIsStable() throws Exception {
        PageInfo pageInfo = PageInfo.toPageInfo(2, 5, 10);
        PageResponse<String> response = PageResponse.toPageResponse(pageInfo, List.of("a", "b"));

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(fieldNames(json)).containsExactly("pageInfo", "dataList");
        assertThat(fieldNames(json.get("pageInfo"))).containsExactly("currentPage", "totalPage", "pageSize");
        assertThat(json.get("pageInfo").get("currentPage").asInt()).isEqualTo(2);
        assertThat(json.get("pageInfo").get("totalPage").asInt()).isEqualTo(5);
        assertThat(json.get("pageInfo").get("pageSize").asInt()).isEqualTo(10);
        assertThat(json.get("dataList").get(0).asText()).isEqualTo("a");
        assertThat(json.get("dataList").get(1).asText()).isEqualTo("b");
    }

    @Test
    void emptyPageResponseJsonContractIsStable() throws Exception {
        PageInfo pageInfo = PageInfo.toPageInfo(0, 0, 10);
        PageResponse<String> response = PageResponse.toPageResponse(pageInfo, List.of());

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(fieldNames(json)).containsExactly("pageInfo", "dataList");
        assertThat(json.get("pageInfo").get("currentPage").asInt()).isEqualTo(0);
        assertThat(json.get("pageInfo").get("totalPage").asInt()).isEqualTo(0);
        assertThat(json.get("pageInfo").get("pageSize").asInt()).isEqualTo(10);
        assertThat(json.get("dataList").isArray()).isTrue();
        assertThat(json.get("dataList")).isEmpty();
    }

    private static List<String> fieldNames(JsonNode json) {
        Iterator<String> names = json.fieldNames();
        List<String> fieldNames = new ArrayList<>();
        while (names.hasNext()) {
            fieldNames.add(names.next());
        }
        return fieldNames;
    }
}
