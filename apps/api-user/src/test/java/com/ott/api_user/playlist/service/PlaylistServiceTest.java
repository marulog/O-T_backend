package com.ott.api_user.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_user.playlist.dto.response.TagPlaylistResponse;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.category.domain.Category;
import com.ott.domain.common.MediaType;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.media.repository.TagContentProjection;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.tag.repository.TagRepository;
import com.ott.infra.db.watch_history.repository.RecentWatchProjection;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// 사용자에게 특정 태그에 기반한 추천 콘텐츠를 제공하거나
// 사용자의 최근 시청 기록(이어보기 목록)을 페이징 처리하여 반환하는 기본적인 플레이리스트 검증
@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private ContentsRepository contentsRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @InjectMocks
    private PlaylistService playlistService;

    // 태그 기반 추천 콘텐츠 조회
    @Test
    void getRecommendContentsByTag_returnsMappedResponses() {
        Long memberId = 1L;
        Long tagId = 2L;

        Member member = Member.builder()
                .id(memberId)
                .email("tester@ott.com")
                .nickname("tester")
                .role(Role.MEMBER)
                .provider(Provider.KAKAO)
                .build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        Category tagCategory = Category.builder().id(1L).name("default").build();
        Tag tag = Tag.builder().id(tagId).category(tagCategory).name("tag").build();
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));

        TagContentProjection projection = new TagContentProjection(10L, "poster-url", MediaType.CONTENTS);
        when(mediaRepository.findRecommendContentsByTagId(tagId, 20))
                .thenReturn(List.of(projection));

        List<TagPlaylistResponse> result = playlistService.getRecommendContentsByTag(memberId, tagId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMediaId()).isEqualTo(10L);
        assertThat(result.get(0).getPosterUrl()).isEqualTo("poster-url");
        assertThat(result.get(0).getMediaType()).isEqualTo(MediaType.CONTENTS);
    }

    // 존재하지 않는 회원 예외 처리
    // 잘못된 회원 ID 로 요청이 들어온 경우, USER_NOT_FOUND 라는 비즈니스 예외를 정상적으로 발생시키는지 검증
    @Test
    void getRecommendContentsByTag_throwsWhenMemberMissing() {
        Long memberId = 5L;
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playlistService.getRecommendContentsByTag(memberId, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(tagRepository, never()).findById(any());
    }

    // 시청 기록 재생 목록 조회
    // 사용자의 최근 시청 기록을 페이지네이션 객체에 맞게 가져오는지 확인
    // 현재 페이지, 총 페이지수, 그리고 각 영상의 시청 길이 등의 데이터가 응답 객체에 올바르게 매핑되는지
    @Test
    void getWatchHistoryPlaylist_returnsPageResult() {
        Long memberId = 3L;
        int page = 2;

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(
                Member.builder()
                        .id(memberId)
                        .email("watcher@ott.com")
                        .nickname("watcher")
                        .role(Role.MEMBER)
                        .provider(Provider.KAKAO)
                        .build()));

        Pageable pageable = PageRequest.of(page, 10);
        List<RecentWatchProjection> projections = List.of(
                new RecentWatchProjection(11L, MediaType.SERIES, "first-poster", 30, 600),
                new RecentWatchProjection(12L, MediaType.CONTENTS, "second-poster", 0, 360)
        );
        Page<RecentWatchProjection> watchHistoryPage = new PageImpl<>(projections, pageable, 5);

        when(watchHistoryRepository.findWatchHistoryByMemberId(memberId, pageable)).thenReturn(watchHistoryPage);

        var response = playlistService.getWatchHistoryPlaylist(memberId, page);

        assertThat(response.getPageMetadata().getCurrentPage()).isEqualTo(page);
        assertThat(response.getPageMetadata().getTotalPage()).isEqualTo(watchHistoryPage.getTotalPages());
        assertThat(response.getDataList()).hasSize(2);
        assertThat(response.getDataList().get(0).getMediaId()).isEqualTo(11L);
        assertThat(response.getDataList().get(1).getDuration()).isEqualTo(360);
    }
}
