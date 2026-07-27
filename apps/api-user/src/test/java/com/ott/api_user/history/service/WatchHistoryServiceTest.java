package com.ott.api_user.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_user.event.WatchHistoryCreatedEvent;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class WatchHistoryServiceTest {

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private ContentsRepository contentsRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WatchHistoryService watchHistoryService;

    @Test
    void upsertWatchHistory_publishesEvent() {
        Long memberId = 5L;
        Long mediaId = 9L;
        Contents contents = createContents(mediaId);

        when(contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC))
                .thenReturn(Optional.of(contents));

        watchHistoryService.upsertWatchHistory(memberId, mediaId);

        verify(watchHistoryRepository).upsertWatchHistory(memberId, contents.getId());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(WatchHistoryCreatedEvent.class);
    }

    @Test
    void upsertWatchHistory_throwsWhenContentsMissing() {
        when(contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(7L, Status.ACTIVE, PublicStatus.PUBLIC))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchHistoryService.upsertWatchHistory(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTENTS_NOT_FOUND);
    }

    private static Contents createContents(Long mediaId) {
        return Contents.builder()
                .id(100L + mediaId.intValue())
                .media(Media.builder()
                        .id(mediaId)
                        .uploader(Member.builder().id(1L).email("a@ott").nickname("tester").provider(Provider.KAKAO).role(Role.MEMBER).build())
                        .title("title")
                        .description("desc")
                        .posterUrl("poster")
                        .thumbnailUrl("thumb")
                        .bookmarkCount(0L)
                        .likesCount(0L)
                        .mediaType(MediaType.CONTENTS)
                        .publicStatus(PublicStatus.PUBLIC)
                        .mediaStatus(MediaStatus.COMPLETED)
                        .build())
                .actors("actor")
                .duration(120)
                .videoSize(1)
                .originUrl("origin")
                .masterPlaylistUrl("master")
                .build();
    }
}
