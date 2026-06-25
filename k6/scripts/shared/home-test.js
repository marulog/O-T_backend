import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKENS_PATH = __ENV.TOKENS_PATH || '../../data/tokens.json';
const THINK_TIME_SECONDS = Number.parseFloat(__ENV.THINK_TIME_SECONDS || '1');

const tokens = new SharedArray('tokens', () => {
    const parsed = JSON.parse(open(TOKENS_PATH));

    if (!Array.isArray(parsed) || parsed.length === 0) {
        throw new Error('tokens.json이 비어 있습니다. generate-tokens.js를 먼저 실행하세요.');
    }

    return parsed;
});

// 기본적으로 k6의 기본 메트릭은 모든 API의 통계를 보여주기 때문에
// 각 API 마다 몇 ms인지 알기 위해서 커스텀 메트릭 생성
// Trend: 시간 분포 측정 avg, p95, p99, max
// Rate : 비율 측정 (성공 실패)
const errorRate = new Rate('error_rate');
const recommendTrend = new Trend('recommend_duration');
const shortFormTrend = new Trend('short_form_duration');
const radarTrend = new Trend('radar_duration');
const topTagTrend = new Trend('top_tag_duration');

export function createHomeThresholds(overrides = {}) {
    const thresholds = {
        // 테스트가 끝날 경우 해당 기준표와 실제 측정값을 비교하여 P/F 체크
        http_req_failed: ['rate<0.01'],         // 전체 에러율 1% 미만
        http_req_duration: ['p(95)<500'],       // 전체 API p(95) 500ms 미만
        recommend_duration: ['p(95)<500'],      // 추천 API p(95) 800ms 미만
        short_form_duration: ['p(95)<500'],     // 숏폼 API p(95) 500ms 미만
        radar_duration: ['p(95)<500'],          // 레이더 API p(95) 800ms 미만
        top_tag_duration: ['p(95)<500'],        // 태그 Top API p(95) 300ms 미만
    };

    Object.keys(overrides).forEach((key) => {
        thresholds[key] = overrides[key];
    });

    return thresholds;
}

export function runHomeScenario() {
    // __VU -> k6가 자동으로 부여한은 가장유저 번호(1, 2, 3..)로 각 VU마다 서로 다른 유저로 설정
    const user = tokens[(__VU - 1) % tokens.length];
    const cookies = { userAccessToken: user.token };

    // tags/top 3번 호출
    // 요청 전송부터 응답 수신까지의 총 시간을 측정
    for (let index = 0; index < 3; index += 1) {
        const response = http.get(`${BASE_URL}/playlists/tags/top?index=${index}&page=0&size=20`, {
            cookies: cookies,
            tags: { api: `top_tag_${index}` },
        });

        topTagTrend.add(response.timings.duration); // 응답 시간을 topTagTrend에 기록
        errorRate.add(response.status !== 200); // 200 아니면 에러로 카운트
        check(response, { [`top_tag[${index}] 200`]: (res) => res.status === 200 });
    }

    // recommend, short-forms, radar 각 1번씩 호출
    // recommend
    const recommendResponse = http.get(`${BASE_URL}/playlists/recommend?page=0&size=20`, {
        cookies: cookies,
        tags: { api: 'recommend' },
    });
    recommendTrend.add(recommendResponse.timings.duration);
    errorRate.add(recommendResponse.status !== 200);
    check(recommendResponse, { 'recommend 200': (res) => res.status === 200 });

    // shortForm
    const shortFormResponse = http.get(`${BASE_URL}/short-forms?page=0&size=10`, {
        cookies: cookies,
        tags: { api: 'short_form' },
    });
    shortFormTrend.add(shortFormResponse.timings.duration);
    errorRate.add(shortFormResponse.status !== 200);
    check(shortFormResponse, { 'short_form 200': (res) => res.status === 200 });

    // radar
    const radarResponse = http.get(`${BASE_URL}/radar/recommend`, {
        cookies: cookies,
        tags: { api: 'radar' },
    });
    radarTrend.add(radarResponse.timings.duration);
    errorRate.add(radarResponse.status !== 200);
    check(radarResponse, { 'radar 200': (res) => res.status === 200 });

    if (THINK_TIME_SECONDS > 0) {
        sleep(THINK_TIME_SECONDS);
    }
}
