package com.ott.common.web.response;

import com.ott.common.core.response.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class PageResponse<T> {

    @Schema(description = "페이징 처리에 필요한 정보")
    private PageInfo pageInfo;

    @Schema(description = "페이징 처리된 데이터 리스트")
    private List<T> dataList;

    public static <T> PageResponse<T> toPageResponse(PageInfo pageInfo, List<T> dataList) {
        return new PageResponse<>(pageInfo, dataList);
    }

    public static <T> PageResponse<T> from(PageResult<T> pageResult) {
        return PageResponseMapper.from(pageResult);
    }
}
