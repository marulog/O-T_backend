package com.ott.api_user.search.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ott.api_user.search.dto.SearchItemResponse;
import com.ott.api_user.search.service.SearchService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class SearchController implements SearchApi {
   private final SearchService searchService;

   @Override
   public ResponseEntity<SuccessResponse<PageResponse<SearchItemResponse>>> search(
           @RequestParam (value = "searchWord") String searchWord,
           @RequestParam (value = "page", defaultValue = "0") Integer page,
           @RequestParam (value = "size", defaultValue = "24") Integer size) {
       PageResponse<SearchItemResponse> response = PageResponseMapper.from(searchService.search(searchWord, page, size));
       return ResponseEntity.ok(SuccessResponse.of(response));
   }

}
