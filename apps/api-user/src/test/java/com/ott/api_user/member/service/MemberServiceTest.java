package com.ott.api_user.member.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.ott.api_user.auth.client.KakaoUnlinkClient;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.infra.db.click_event.repository.ClickRepository;
import com.ott.infra.db.comment.repository.CommentRepository;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.member_radar_preference.repository.MemberRadarPreferenceRepository;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import com.ott.infra.db.preferred_tag.repository.PreferredTagRepository;
import com.ott.infra.db.tag.repository.TagRepository;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PreferredTagRepository preferredTagRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private WatchHistoryRepository watchHistoryRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private KakaoUnlinkClient kakaoUnlinkClient;
    @Mock
    private BookmarkRepository bookmarkRepository;
    @Mock
    private LikesRepository likesRepository;
    @Mock
    private PlaybackRepository playbackRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ClickRepository clickRepository;
    @Mock
    private MemberRadarPreferenceRepository memberRadarPreferenceRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void withdrawKeepsMemberAndRelatedSoftDeleteOrder() {
        Long memberId = 41L;
        Member member = Member.builder().id(memberId).provider(Provider.LOCAL).build();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        memberService.withdraw(memberId);

        InOrder order = inOrder(
                memberRepository,
                bookmarkRepository,
                likesRepository,
                preferredTagRepository,
                watchHistoryRepository,
                playbackRepository,
                commentRepository,
                memberRadarPreferenceRepository
        );
        order.verify(memberRepository).findById(memberId);
        order.verify(memberRepository).softDeleteByMemberId(memberId);
        order.verify(bookmarkRepository).softDeleteAllByMemberId(memberId);
        order.verify(likesRepository).softDeleteAllByMemberId(memberId);
        order.verify(preferredTagRepository).softDeleteAllByMemberId(memberId);
        order.verify(watchHistoryRepository).softDeleteAllByMemberId(memberId);
        order.verify(playbackRepository).softDeleteAllByMemberId(memberId);
        order.verify(commentRepository).softDeleteAllByMemberId(memberId);
        order.verify(memberRadarPreferenceRepository).softDeleteByMemberId(memberId);
        order.verifyNoMoreInteractions();
    }
}
