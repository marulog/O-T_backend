package com.ott.api_user.moodrefresh.dto.response;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.encrypt.BytesEncryptor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ott.domain.media.domain.Media;
import com.ott.domain.moodrefresh.domain.MemberMoodRefresh;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MoodRefreshResponse {
    private Long refreshId;
    private Byte imageId;
    private String subtitle;
    private List<String> tags; //해시태그 노출용 분석 결과 감정 태그
    private List<MoodRecommendMediaDto> recommendedMediaList;

    public static MoodRefreshResponse of(MemberMoodRefresh refresh, List<Media> mediaList) {
        String rawSubtitle = refresh.getSubtitle();
        String parsedSubtitle = rawSubtitle;
        List<String> parsedTags = List.of();

        //  구분자 '|'가 있다면 쪼개서 각각 넣어줌
        if (rawSubtitle != null && rawSubtitle.contains("|")) {
            String[] parts = rawSubtitle.split("\\|");
            parsedSubtitle = parts[0]; // 앞부분은 추천 멘트
            if (parts.length > 1) {
                // 뒷부분(해시태그) 은 쉼표로 분리하여 리스트로 변환
                parsedTags = List.of(parts[1].split(","));
            }
        }

        List<MoodRecommendMediaDto> mediaDtoList = mediaList.stream()
                .map(MoodRecommendMediaDto::from)
                .collect(Collectors.toList());

        return MoodRefreshResponse.builder()
                .refreshId(refresh.getId())
                .imageId(refresh.getImageId())
                .subtitle(parsedSubtitle) // 💡 멘트만 깔끔하게 전달
                .tags(parsedTags)         // 💡 태그 리스트 전달
                .recommendedMediaList(mediaDtoList)
                .build();
    }
}
