package com.ott.api_admin.ai.client;

import com.ott.api_admin.ai.dto.TaggingRequest;
import com.ott.api_admin.ai.dto.TaggingResponse;

import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final WebClient aiWebClient;

    @Value("${ai.timeout-ms}")
    private Long timeoutMs;

    /**
     * FastAPI 서버에 영상 줄거리를 보내고 감정 태그 리스트를 받아옵니다.
     * 현재는 비동기 + 블로킹으로 AI 서버의 응답을 기다리고 있습니다.
     * 다만, 관리자 서버의 요청 스레드(Tomcat)가 블로킹하는게 아닌, 관리자 서버 요청 스레드는 비동기로 바로 반환되고
     * 비동기 작업에서 사용되는 스레드(Async)로 해당 AI서버의 응답을 블로킹 하기 때문에 더 효율적이라 판단했습니다.
     * 추후, 유저도 업로드로 확장 된다면 비동기 + 논블로킹도 좋은 방법이라 생각됩니다.
     */
    public List<String> getEmotionTags(Long mediaId, String description) {
        log.info("[Admin AI] 미디어 태깅 요청: mediaId={}", mediaId);

        TaggingRequest requestDto = new TaggingRequest(mediaId, description);

        try{
            TaggingResponse response = aiWebClient.post()
                .uri("/tagging")
                .bodyValue(requestDto)
                .retrieve()
                .bodyToMono(TaggingResponse.class)
                .timeout(Duration.ofMillis(timeoutMs)) // 해당 시간까지 AI작업이 끝나야함을 명시
                .block(); // 비동기 작업 내에서 안전하게 블로킹 처리

            if (response == null || response.getMoodTags() == null) {
                log.warn("[Admin AI] 태깅 응답이 없거나 moodTags가 null입니다. 빈 리스트를 반환합니다. mediaId={}", mediaId);
                return Collections.emptyList();
            }

            log.info("[Admin AI] 태깅 응답 완료: {}", response.getMoodTags());
            return response.getMoodTags();

        }catch(Exception e){
            // 타임아웃 발생 or AI 서버 죽었을 때
            log.error("[Admin AI] 태깅 요청 실패 (mediaId={}): {}", mediaId, e.getMessage());

            return Collections.emptyList(); // 에러로 중단되지 않게 일단 빈 값으로 반환
        }
    }
}