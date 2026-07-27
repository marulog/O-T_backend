package com.ott.infra.db.media.repository;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.media.domain.Media;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public class MediaRepositoryImpl implements MediaRepositoryCustom {

        private final AdminMediaQueries adminMediaQueries;
        private final MediaRecommendationQueries mediaRecommendationQueries;
        private final MediaPlaylistQueries mediaPlaylistQueries;
        private final MediaSearchQueries mediaSearchQueries;

        public MediaRepositoryImpl(JPAQueryFactory queryFactory) {
                this.adminMediaQueries = new AdminMediaQueries(queryFactory);
                this.mediaRecommendationQueries = new MediaRecommendationQueries(queryFactory);
                this.mediaPlaylistQueries = new MediaPlaylistQueries(queryFactory);
                this.mediaSearchQueries = new MediaSearchQueries(queryFactory);
        }

        @Override
        public Page<Media> findMediaListByMediaTypeAndSearchWord(Pageable pageable, MediaType mediaType,
                        String searchWord) {
                return adminMediaQueries.findMediaListByMediaTypeAndSearchWord(pageable, mediaType, searchWord);
        }

        @Override
        public Page<Media> findMediaListByMediaTypeAndSearchWordAndPublicStatus(Pageable pageable, MediaType mediaType,
                        String searchWord, PublicStatus publicStatus) {
                return adminMediaQueries.findMediaListByMediaTypeAndSearchWordAndPublicStatus(pageable, mediaType,
                                searchWord, publicStatus);
        }

        @Override
        public Page<Media> findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(Pageable pageable,
                        MediaType mediaType, String searchWord, PublicStatus publicStatus, Long uploaderId) {
                return adminMediaQueries.findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(pageable,
                                mediaType, searchWord, publicStatus, uploaderId);
        }

        @Override
        public Page<Media> findOriginMediaListBySearchWord(Pageable pageable, String searchWord) {
                return adminMediaQueries.findOriginMediaListBySearchWord(pageable, searchWord);
        }

        @Override
        public List<TagContentProjection> findRecommendContentsByTagId(Long tagId, int limit) {
                return mediaRecommendationQueries.findRecommendContentsByTagId(tagId, limit);
        }

        @Override
        public List<Media> findMediasByTagId(Long tagId, Long excludeMediaId, int limit, long offset) {
                return mediaRecommendationQueries.findMediasByTagId(tagId, excludeMediaId, limit, offset);
        }

        @Override
        public Page<Media> findTrendingPlaylists(MediaType mediaType, Long excludeMediaId, Pageable pageable) {
                return mediaPlaylistQueries.findTrendingPlaylists(mediaType, excludeMediaId, pageable);
        }

        @Override
        public Page<Media> findHistoryPlaylists(Long memberId, MediaType mediaType, Long excludeMediaId,
                        Pageable pageable) {
                return mediaPlaylistQueries.findHistoryPlaylists(memberId, mediaType, excludeMediaId, pageable);
        }

        @Override
        public Page<Media> findBookmarkedPlaylists(Long memberId, MediaType mediaType, Long excludeMediaId,
                        Pageable pageable) {
                return mediaPlaylistQueries.findBookmarkedPlaylists(memberId, mediaType, excludeMediaId, pageable);
        }

        @Override
        public Page<Media> findPlaylistsByTag(Long tagId, MediaType mediaType, Long excludeMediaId,
                        Pageable pageable) {
                return mediaPlaylistQueries.findPlaylistsByTag(tagId, mediaType, excludeMediaId, pageable);
        }

        @Override
        public List<Media> findMediasByTagId(Long tagId, MediaType mediaType, Long excludeMediaId, int limit,
                        long offset) {
                return mediaPlaylistQueries.findMediasByTagId(tagId, mediaType, excludeMediaId, limit, offset);
        }

        @Override
        public List<Media> findRecommendedMedias(Map<Long, Integer> tagScores, MediaType mediaType,
                        Long excludeMediaId, int limit, long offset) {
                return mediaRecommendationQueries.findRecommendedMedias(tagScores, mediaType, excludeMediaId, limit,
                                offset);
        }

        @Override
        public Page<Media> findUserSearchMediaList(Pageable pageable, String searchWord) {
                return mediaSearchQueries.findUserSearchMediaList(pageable, searchWord);
        }

        @Override
        public List<Media> findByTop3ByMoodTagName(String tagName) {
                return mediaSearchQueries.findByTop3ByMoodTagName(tagName);
        }
}
