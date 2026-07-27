package com.ott.api_user.history.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ott.api_user.history.dto.request.WatchHistoryRequest;
import com.ott.api_user.history.service.WatchHistoryService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/watch-history")
public class WatchHistoryController implements WatchHistoryApi{

    private final WatchHistoryService watchHistoryService;

    @Override
    @PutMapping
    public ResponseEntity<Void> upsertWatchHistory(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody WatchHistoryRequest request){

            watchHistoryService.upsertWatchHistory(memberId, request.getMediaId());
                return ResponseEntity.noContent().build(); // 204 No Content
        }
}
