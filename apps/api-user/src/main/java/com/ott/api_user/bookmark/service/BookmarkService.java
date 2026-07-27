package com.ott.api_user.bookmark.service;

import com.ott.api_user.bookmark.dto.response.BookmarkMediaResponse;
import com.ott.api_user.bookmark.dto.response.BookmarkShortFormResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.bookmark.domain.Bookmark;
import com.ott.infra.db.bookmark.repository.BookmarkMediaProjection;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.Status;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkService {

        private final BookmarkRepository bookmarkRepository;
        private final MemberRepository memberRepository;
        private final MediaRepository mediaRepository;
        private final ContentsRepository contentsRepository;

        /**
         * 북마크 수정
         * CONTENTS → 시리즈 에피소드면 부모 Series.media로 처리
         * CONTENTS → 시리즈가 아닐 경우 자기 자신 그대로 처리
         * SHORT_FORM → 자기 자신 그대로 처리
         * SERIES → 자기 자신 그대로 처리
         * 해당 메소드로 bookmark 테이블에는 시리즈 / 단편 시나리오 / 숏폼만 저장됨
         */
        @Transactional
        public void editBookmark(Long memberId, Long mediaId) {

                Media findMedia = mediaRepository.findById(mediaId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));

                // 실제 북마크 처리할 타겟 미디어 결정
                Media targetMedia = resolveTargetMedia(findMedia);

                // 해당 유저가 해당 미디어에 대해서 북마크를 했는지 여부 체크
                bookmarkRepository.findByMemberIdAndMediaId(memberId, targetMedia.getId())
                                .ifPresentOrElse(
                                                bookmark -> {
                                                        // 이미 해당 미디어에 대해서 북마크한 경우 -> DELETE 변경 이후 + 카운트 감소
                                                        if (bookmark.getStatus() == Status.ACTIVE) {
                                                                bookmark.updateStatus(Status.DELETE);
                                                                targetMedia.decreaseBookmarkCount();
                                                        } else {
                                                                // 해당 미디어에 대해 북마크를 안한 경우 -> ACTIVE 변경 이후 + 카운트 증가
                                                                bookmark.updateStatus(Status.ACTIVE);
                                                                targetMedia.increaseBookmarkCount();
                                                        }
                                                },
                                                () -> {
                                                        // 행 자체가 없음 -> 신규임 -> insert이후 상태 ACTIVE + 카운트 증가
                                                        Member findMember = memberRepository.findById(memberId)
                                                                        .orElseThrow(() -> new BusinessException(
                                                                                        ErrorCode.USER_NOT_FOUND));

                                                        bookmarkRepository.save(Bookmark.builder()
                                                                        .member(findMember)
                                                                        .media(targetMedia)
                                                                        .build()); // 상태 default가 ACTIVE임

                                                        targetMedia.increaseBookmarkCount();
                                                });
        }

        // 북마크 리스트 조회
        // 이미 DB상에는 시리즈 원본 / 시나리오 / 숏폼만 저장되어 있음
        @Transactional(readOnly = true)
        public PageResult<BookmarkMediaResponse> getBookmarkMediaList(Long memberId, Integer page, Integer size) {

                Pageable pageable = PageRequest.of(page, size);

                Page<BookmarkMediaProjection> bookmarkPage =
                        bookmarkRepository.findBookmarkMediaList(memberId, pageable);

                List<BookmarkMediaResponse> dataList = bookmarkPage.getContent()
                        .stream()
                        .map(BookmarkMediaResponse::from)
                        .toList();

                // pageInfo 생성
                PageMetadata pageMetadata = PageMetadata.of(
                                bookmarkPage.getNumber(),
                                bookmarkPage.getTotalPages(),
                                bookmarkPage.getSize());

                return PageResult.of(pageMetadata, dataList);
        }

        // 숏폼 리스트 조회
        @Transactional(readOnly = true)
        public PageResult<BookmarkShortFormResponse> getBookmarkShortFormList(Long memberId, Integer page,
                        Integer size) {

                Pageable pageable = PageRequest.of(page, size);

                // SHORT_FORM 타입만 조회
                Page<Bookmark> bookmarkPage = bookmarkRepository
                                .findByMemberIdAndStatusAndMedia_MediaTypeOrderByCreatedDateDesc(
                                                memberId,
                                                Status.ACTIVE,
                                                MediaType.SHORT_FORM,
                                                pageable);

                // DTO 변환
                List<BookmarkShortFormResponse> dataList = bookmarkPage.getContent().stream()
                                .map(BookmarkShortFormResponse::from)
                                .toList();

                // pageInfo 생성
                PageMetadata pageMetadata = PageMetadata.of(
                                bookmarkPage.getNumber(),
                                bookmarkPage.getTotalPages(),
                                bookmarkPage.getSize());

                return PageResult.of(pageMetadata, dataList);
        }

        /**
         * mediaType에 따라 실제 북마크 처리할 타겟 Media 반환
         * CONTENTS → series 소속이면 series.media 반환
         * CONTENTS → series 소속이 아니면 자기 자신 media 반환
         * SHORT_FORM → 자기 자신 media 반환
         * SERIES → 자기 자신 media 반환
         */
        private Media resolveTargetMedia(Media media) {
                return switch (media.getMediaType()) {
                        case CONTENTS -> contentsRepository.findByMediaId(media.getId())
                                        .filter(contents -> contents.getSeries() != null)
                                        .map(contents -> contents.getSeries().getMedia())
                                        .orElse(media);

                        case SERIES, SHORT_FORM -> media;
                };
        }
}
