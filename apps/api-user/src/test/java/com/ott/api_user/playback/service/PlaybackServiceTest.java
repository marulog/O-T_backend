package com.ott.api_user.playback.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaybackServiceTest {

    @Mock
    private PlaybackRepository playbackRepository;

    @Mock
    private ContentsRepository contentsRepository;

    @InjectMocks
    private PlaybackService playbackService;

    @Test
    void upsertPlayback_defaultsNegativePositionToZero() {
        Long memberId = 10L;
        Long mediaId = 20L;
        when(contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC))
                .thenReturn(Optional.of(createContents(mediaId)));

        playbackService.upsertPlayback(memberId, mediaId, -5);

        verify(playbackRepository).upsertPlayback(memberId, mediaId, 0);
    }

    @Test
    void upsertPlayback_throwsWhenContentMissing() {
        when(contentsRepository.findByMediaIdAndStatusAndMedia_PublicStatus(5L, Status.ACTIVE, PublicStatus.PUBLIC))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> playbackService.upsertPlayback(1L, 5L, 10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTENTS_NOT_FOUND);
    }

    private static Contents createContents(Long mediaId) {
        return Contents.builder()
                .id(mediaId)
                .media(Media.builder()
                        .id(mediaId)
                        .uploader(com.ott.domain.member.domain.Member.builder().id(1L).email("a@ott").nickname("m").provider(com.ott.domain.member.domain.Provider.KAKAO).role(com.ott.domain.member.domain.Role.MEMBER).build())
                        .title("title-" + mediaId)
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
                .duration(100)
                .videoSize(1)
                .originUrl("origin")
                .masterPlaylistUrl("master")
                .build();
    }
}
