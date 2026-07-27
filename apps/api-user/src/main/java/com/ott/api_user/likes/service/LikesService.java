package com.ott.api_user.likes.service;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.Status;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.likes.domain.Likes;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikesService {

    private final LikesRepository likesRepository;
    private final MemberRepository memberRepository;
    private final MediaRepository mediaRepository;
    private final ContentsRepository contentsRepository;

    /**
     * 좋아요 버튼
     * CONTENTS → 시리즈 에피소드면 부모 Series.media로 처리
     * CONTENTS → 시리즈가 아닐경우 자기 자신 그대로 처리
     * SHORT_FORM → 그대로 처리
     * SERIES → 그대로 처리
     */
    @Transactional
    public void editLikes(Long memberId, Long mediaId) {

        Media findMedia = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));

        // 실제 좋아요 처리할 미디어 결정
        Media targetMedia = resolveTargetMedia(findMedia);

        // likes 테이블에서 처음 등록했는지 여부를 판단함
        likesRepository.findByMemberIdAndMediaId(memberId, targetMedia.getId())
                .ifPresentOrElse(
                        likes -> {

                            if (likes.getStatus() == Status.ACTIVE) {
                                // 기록이 있을 경우 ACTIVE → DELETE + 카운트 감소
                                likes.updateStatus(Status.DELETE);
                                targetMedia.decreaseLikesCount();
                            } else {
                                // 기록이 없을 경우 DELETE → ACTIVE + 카운트 증가
                                likes.updateStatus(Status.ACTIVE);
                                targetMedia.increaseLikesCount();
                            }
                        },
                        () -> {
                            // 신규 좋아요일 경우 insert + 카운트 증가
                            Member findMember = memberRepository.findById(memberId)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                            // insert
                            likesRepository.save(Likes.builder()
                                    .member(findMember)
                                    .media(targetMedia)
                                    .build());

                            // 카운트 증가
                            targetMedia.increaseLikesCount();
                        });
    }

    /**
     * mediaType에 따라 실제 좋아요 처리할 타겟 Media 반환
     * CONTENTS → series 소속이면 series.media 반환
     * CONTENTS → series 소속이 아나면 자기 자신 media 반환
     * SHORT_FORM → 자기 자신 media 반환
     * SERIES → 자기 자신 series 반환
     */
    private Media resolveTargetMedia(Media media) {
        return switch (media.getMediaType()) {
            case CONTENTS -> contentsRepository.findByMediaId(media.getId())
                    .filter(contents -> contents.getSeries() != null) // 시리즈 에피소드인지 확인
                    .map(contents -> contents.getSeries().getMedia()) // 부모 Series.media로 교체
                    .orElse(media); // 단편이면 그대로

            case SERIES, SHORT_FORM -> media; // 시리즈 자체 or 숏폼은 항상 그대로
        };
    }
}
