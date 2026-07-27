package com.ott.common.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseMapperTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void mapsPageResultToPageResponseWithoutChangingJsonContract() throws Exception {
        PageInfo pageInfo = PageInfo.toPageInfo(2, 5, 10);
        PageResponse<String> currentContract = PageResponse.toPageResponse(pageInfo, List.of("a", "b"));
        PageResult<String> pageResult = PageResult.of(PageMetadata.of(2, 5, 10), List.of("a", "b"));

        PageResponse<String> mapped = PageResponseMapper.from(pageResult);

        assertThat(jsonMapper.writeValueAsString(mapped))
                .isEqualTo(jsonMapper.writeValueAsString(currentContract));
    }

    @Test
    void mapsEmptyDataListAsEmptyArrayNotNull() throws Exception {
        PageInfo pageInfo = PageInfo.toPageInfo(0, 0, 10);
        PageResponse<String> currentContract = PageResponse.toPageResponse(pageInfo, List.of());
        PageResult<String> pageResult = PageResult.of(PageMetadata.of(0, 0, 10), List.of());

        PageResponse<String> mapped = PageResponseMapper.from(pageResult);

        assertThat(mapped.getDataList()).isEmpty();
        assertThat(jsonMapper.writeValueAsString(mapped))
                .isEqualTo(jsonMapper.writeValueAsString(currentContract));
        assertThat(jsonMapper.readTree(jsonMapper.writeValueAsString(mapped)).get("dataList").isArray())
                .isTrue();
    }
}
