package com.ott.api_user.shortform.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.click_event.domain.ClickEvent;
import com.ott.domain.click_event.domain.ClickType;
import com.ott.infra.db.click_event.repository.ClickRepository;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.domain.short_form.domain.ShortForm;
import com.ott.infra.db.short_form.repository.ShortFormRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ClickEventService {
    private final ClickRepository clickRepository;
    private final MemberRepository memberRepository;
    private final ShortFormRepository shortFormRepository;

    public void saveClickEvent(Long memberId, Long mediaId, ClickType clickType){
        Member member = memberRepository.findById(memberId)
            .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ShortForm shortForm = shortFormRepository.findByMediaId(mediaId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHORT_FORM_NOT_FOUND));

        ClickEvent event = ClickEvent.builder()
            .member(member)
            .shortForm(shortForm)
            .clickType(clickType)
            .clickAt(LocalDateTime.now())
            .build();

        clickRepository.save(event);
    }
}
