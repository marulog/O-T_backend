package com.ott.api_user.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ott.api_user.auth.client.KakaoUnlinkClient;
import com.ott.api_user.member.service.MemberService;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExternalCallOrderContractTest {

    @Test
    void withdrawKakaoMember_emitsBaselineExternalCallSequence() throws IOException {
        List<String> trace = new ArrayList<>();
        MemberRepository members = mock(MemberRepository.class);
        PreferredTagRepository preferredTags = mock(PreferredTagRepository.class);
        TagRepository tags = mock(TagRepository.class);
        WatchHistoryRepository history = mock(WatchHistoryRepository.class);
        MediaRepository media = mock(MediaRepository.class);
        KakaoUnlinkClient kakao = mock(KakaoUnlinkClient.class);
        BookmarkRepository bookmarks = mock(BookmarkRepository.class);
        LikesRepository likes = mock(LikesRepository.class);
        PlaybackRepository playback = mock(PlaybackRepository.class);
        CommentRepository comments = mock(CommentRepository.class);
        ClickRepository clicks = mock(ClickRepository.class);
        MemberRadarPreferenceRepository radar = mock(MemberRadarPreferenceRepository.class);
        Member member = Member.builder()
                .id(41L)
                .provider(Provider.KAKAO)
                .providerId("kakao-41")
                .build();

        when(members.findById(41L)).thenAnswer(invocation -> event(
                trace, "api-user.member.withdraw -> memberRepository.findById", Optional.of(member)));
        doAnswer(invocation -> event(trace, "api-user.member.withdraw -> kakaoUnlinkClient.unlink", null))
                .when(kakao).unlink("kakao-41");
        doAnswer(invocation -> event(trace, "api-user.member.withdraw -> memberRepository.softDeleteByMemberId", null))
                .when(members).softDeleteByMemberId(41L);
        doAnswer(invocation -> event(trace, "api-user.member.withdraw -> bookmarkRepository.softDeleteAllByMemberId", null))
                .when(bookmarks).softDeleteAllByMemberId(41L);
        doAnswer(invocation -> event(trace, "api-user.member.withdraw -> likesRepository.softDeleteAllByMemberId", null))
                .when(likes).softDeleteAllByMemberId(41L);
        doAnswer(invocation -> event(
                trace, "api-user.member.withdraw -> preferredTagRepository.softDeleteAllByMemberId", null))
                .when(preferredTags).softDeleteAllByMemberId(41L);
        doAnswer(invocation -> event(
                trace, "api-user.member.withdraw -> watchHistoryRepository.softDeleteAllByMemberId", null))
                .when(history).softDeleteAllByMemberId(41L);
        doAnswer(invocation -> event(trace, "api-user.member.withdraw -> playbackRepository.softDeleteAllByMemberId", null))
                .when(playback).softDeleteAllByMemberId(41L);
        doAnswer(invocation -> event(trace, "api-user.member.withdraw -> commentRepository.softDeleteAllByMemberId", null))
                .when(comments).softDeleteAllByMemberId(41L);
        doAnswer(invocation -> event(
                trace, "api-user.member.withdraw -> memberRadarPreferenceRepository.softDeleteByMemberId", null))
                .when(radar).softDeleteByMemberId(41L);
        MemberService service = new MemberService(
                members, preferredTags, tags, history, media, kakao,
                bookmarks, likes, playback, comments, clicks, radar);

        service.withdraw(41L);

        assertTrace("contracts/external-call-order/api-user-member-withdraw.txt", trace);
    }

    private static <T> T event(List<String> trace, String name, T result) {
        trace.add(name);
        return result;
    }

    private static void assertTrace(String resource, List<String> trace) throws IOException {
        InputStream stream = ExternalCallOrderContractTest.class.getClassLoader().getResourceAsStream(resource);
        assertThat(stream).as("external call order fixture %s", resource).isNotNull();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            assertThat(trace).containsExactlyElementsOf(
                    reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList());
        }
        trace.forEach(line -> System.out.println("EXTERNAL_CALL_ORDER_TRACE " + line));
    }
}
