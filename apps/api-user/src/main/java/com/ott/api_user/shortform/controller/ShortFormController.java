package com.ott.api_user.shortform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ott.api_user.shortform.dto.request.ShortFormEventRequest;
import com.ott.api_user.shortform.dto.response.ShortFormFeedResponse;
import com.ott.api_user.shortform.service.ClickEventService;
import com.ott.api_user.shortform.service.ShortFormFeedService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import com.ott.domain.click_event.domain.ClickType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/short-forms")
public class ShortFormController implements ShortFormApi {

    private final ShortFormFeedService shortFormFeedService;
    private final ClickEventService clickEventService;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ShortFormFeedResponse>>> getShortFormFeed(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {

        PageResponse<ShortFormFeedResponse> response = PageResponseMapper.from(shortFormFeedService.getShortFormFeed(memberId, page, size));
        return ResponseEntity.ok(SuccessResponse.of(response));
    }

    @Override
    @PostMapping("/events")
    public ResponseEntity<Void> recordShortFormView(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ShortFormEventRequest request) {

        clickEventService.saveClickEvent(memberId, request.getMediaId(), ClickType.SHORT_CLICK);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/cta")
    public ResponseEntity<Void> recordCtaClick(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ShortFormEventRequest request) {

        clickEventService.saveClickEvent(memberId, request.getMediaId(), ClickType.CTA_CLICK);
        return ResponseEntity.noContent().build();
    }
}
