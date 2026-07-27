package com.ott.api_user.search.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ott.api_user.search.dto.SearchItemResponse;
import com.ott.api_user.search.service.SearchService;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController searchController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchController).build();
    }

    @Test
    void search_returnsSuccessResponseWithPageInfoAndDataList() throws Exception {
        SearchItemResponse item = SearchItemResponse.builder()
                .mediaId(101L)
                .title("keyword result")
                .posterUrl("poster")
                .mediaType(null)
                .build();
        PageResult<SearchItemResponse> result = PageResult.of(
                PageMetadata.of(0, 3, 24),
                List.of(item)
        );

        when(searchService.search("keyword", 0, 24)).thenReturn(result);

        mockMvc.perform(get("/search")
                        .param("searchWord", "keyword")
                        .param("page", "0")
                        .param("size", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageInfo.currentPage").value(0))
                .andExpect(jsonPath("$.data.pageInfo.totalPage").value(3))
                .andExpect(jsonPath("$.data.pageInfo.pageSize").value(24))
                .andExpect(jsonPath("$.data.dataList[0].mediaId").value(101L))
                .andExpect(jsonPath("$.data.dataList[0].title").value("keyword result"));

        verify(searchService).search("keyword", 0, 24);
    }

    @Test
    void search_outOfRangePagePreservesEmptyPageResponseShape() throws Exception {
        PageResult<SearchItemResponse> result = PageResult.of(
                PageMetadata.of(5, 2, 24),
                List.of()
        );

        when(searchService.search("keyword", 5, 24)).thenReturn(result);

        mockMvc.perform(get("/search")
                        .param("searchWord", "keyword")
                        .param("page", "5")
                        .param("size", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageInfo.currentPage").value(5))
                .andExpect(jsonPath("$.data.pageInfo.totalPage").value(2))
                .andExpect(jsonPath("$.data.pageInfo.pageSize").value(24))
                .andExpect(jsonPath("$.data.dataList.length()").value(0));

        verify(searchService).search("keyword", 5, 24);
    }
}
