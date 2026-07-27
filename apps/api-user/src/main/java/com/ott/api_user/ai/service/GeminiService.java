package com.ott.api_user.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import com.ott.api_user.ai.dto.GeminiRequest;
import com.ott.api_user.ai.dto.GeminiResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;


@Service
public class GeminiService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public String generateHealingMessage(String userMood, List<String> recommendedTags) {
        // 1. 파이썬에서 받은 태그 리스트를 하나의 문자열로 결합 ("힐링, 도파민_폭발, 평화로운")
        String tagsStr = String.join(", ", recommendedTags);

        // 2. 프롬프트 엔지니어링
        String promptText = String.format(
            "너는 트렌디한 스트리밍 앱 'O+T'의 메인 카피라이터야. " +
            "유저의 최근 시청 분위기 리스트는 '%s'이고, 분위기 전환용 추천 태그는 '%s'야. " +
            "아래 [기본 구조]를 따르되, 괄호 [ ] 안의 내용을 매번 센스 있게 변형해서 카피를 작성해줘.\n" +
            "[기본 구조]: \"최근 [A] 시청 이력을 보셨군요. [B] 오늘, [C]\"\n" +
            "작성 규칙:\n" +
            "1. [A]: 전달받은 최근 시청 분위기 리스트 중 **무조건 첫 번째 태그와 두번째 태그**만을 기준으로 삼아, 그 느낌을 살린 형용사로 작성할 것\n" +
            "2. [B]: [A]를 바탕으로 유저의 현재 기분이나 오늘 하루를 유추한 수식어 (예: 생각이 많은, 텐션 올리고 싶은, 머리 비우고 싶은 등)\n" +
            "3. [C]: 추천 태그를 활용해 클릭을 유도하는 힙하고 짧은 추천 문구\n" +
            "제한 사항:\n" +
            "- 전체 글자 수는 70자 이내로 2문장을 넘지 않게 아주 짧게 쓸 것.\n" +
            "- '안녕하세요', 'O+T입니다', '위로', '진심' 같은 오글거리는 단어나 인사말 절대 금지.",
            userMood, tagsStr
        );

        try {
            // 3. API 요청 객체 조립
            GeminiRequest.Part part = new GeminiRequest.Part(promptText);
            GeminiRequest.Content content = new GeminiRequest.Content(List.of(part));
            GeminiRequest requestBody = new GeminiRequest(List.of(content));
            String requestUrl = geminiApiUrl + "?key=" + geminiApiKey;
            HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl))
                    .timeout(READ_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            // 5. API 호출!
            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GeminiResponse response = objectMapper.readValue(httpResponse.body(), GeminiResponse.class);

            // 6. 응답에서 알맹이 텍스트만 쏙 빼오기
            if (response != null && !response.candidates().isEmpty()) {
                return response.candidates().get(0).content().parts().get(0).text();
            }
            return "오늘 하루도 수고 많으셨어요. O+T가 추천하는 영상과 함께 편안한 시간 보내세요."; // Fallback 멘트

        } catch (Exception e) {
            System.err.println("Gemini API 호출 실패: " + e.getMessage());
            return "추천 영상과 함께 기분 좋은 시간 보내시길 바랄게요!"; // 서버 에러 시 기본 멘트
        }
    }
}
