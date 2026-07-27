package com.ott.api_user.auth.client;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoUnlinkClient {

    private final RestTemplate restTemplate;

    @Value("${kakao.unlink-url}")
    private String unlinkUrl;

    @Value("${kakao.admin-key}")
    private String adminKey;

    /**
     * 카카오 연결 끊기 (어드민 키 방식)
     * 탈퇴 시 카카오 서버에서 해당 유저와의 연결을 끊음
     */
    public void unlink(String providerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "KakaoAK " + adminKey);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("target_id_type", "user_id");
        body.add("target_id", providerId);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(unlinkUrl, request, String.class);
            log.info("카카오 연결 끊기 성공");
        } catch (Exception e) {
            log.error("카카오 연결 끊기 실패");
            throw new BusinessException(ErrorCode.KAKAO_UNLINK_FAILED);
        }
    }
}