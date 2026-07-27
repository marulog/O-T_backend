package com.ott.api_user.radar_preference.controller;

import com.ott.api_user.radar_preference.dto.request.RadarPreferenceRequest;
import com.ott.api_user.radar_preference.dto.response.RadarMediaResponse;
import com.ott.api_user.radar_preference.dto.response.RadarPreferenceResponse;
import com.ott.api_user.radar_preference.service.RadarPreferenceService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/radar")
@RequiredArgsConstructor
public class RadarPreferenceController implements RadarPreferenceApi {

    private final RadarPreferenceService radarPreferenceService;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<RadarPreferenceResponse>> getPreference(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(radarPreferenceService.getPreference(memberId))
        );
    }

    @Override
    @PutMapping
    public ResponseEntity<Void> updatePreference(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RadarPreferenceRequest request
    ) {
        radarPreferenceService.updatePreference(memberId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/recommend")
    public ResponseEntity<SuccessResponse<PageResponse<RadarMediaResponse>>> getRecommendationList(
            @RequestParam(value = "excludeMediaId", required = false) Long excludeMediaId,
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(radarPreferenceService.getRecommendations(memberId, excludeMediaId)))
        );
    }
}
