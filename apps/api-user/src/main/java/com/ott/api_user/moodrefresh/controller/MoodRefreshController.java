package com.ott.api_user.moodrefresh.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ott.api_user.moodrefresh.dto.response.MoodRefreshResponse;
import com.ott.api_user.moodrefresh.service.MoodRefreshService;
import com.ott.common.web.response.SuccessResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mood-refresh")
public class MoodRefreshController implements MoodRefreshApi {

    private final MoodRefreshService moodRefreshService;

    @Override
    @GetMapping("/active")
    public ResponseEntity<SuccessResponse<MoodRefreshResponse>> getActiveCard(
            @AuthenticationPrincipal Long memberId) {

        MoodRefreshResponse response = moodRefreshService.getActiveRefreshCard(memberId);
        return ResponseEntity.ok(SuccessResponse.of(response));
    }

    @Override
    @PatchMapping("/{refreshId}/hide")
    public ResponseEntity<Void> hideCard(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("refreshId") Long refreshId) {

        moodRefreshService.hideRefreshCard(memberId, refreshId);
        return ResponseEntity.noContent().build();
    }
}