package com.ott.common.core.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void createsHttpIndependentPageResult() {
        PageMetadata metadata = PageMetadata.of(1, 3, 20);
        PageResult<String> result = PageResult.of(metadata, List.of("item"));

        assertThat(result.getPageMetadata()).isSameAs(metadata);
        assertThat(result.getDataList()).containsExactly("item");
    }
}
