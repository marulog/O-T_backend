// k6 run .\k6\scripts\trending.js
// k6 run -e VUS=50 -e DURATION=1m .\k6\scripts\trending.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKENS_PATH = __ENV.TOKENS_PATH || '../data/tokens.json';
const THINK_TIME_SECONDS = Number.parseFloat(__ENV.THINK_TIME_SECONDS || '1');
const PAGE = Number.parseInt(__ENV.PAGE || '0', 10);
const SIZE = Number.parseInt(__ENV.SIZE || '20', 10);
const EXCLUDE_MEDIA_ID = __ENV.EXCLUDE_MEDIA_ID || '';

const tokens = new SharedArray('tokens', () => {
    const parsed = JSON.parse(open(TOKENS_PATH));

    if (!Array.isArray(parsed) || parsed.length === 0) {
        throw new Error('tokens.json이 비어 있습니다.');
    }

    return parsed;
});

export const options = {
    vus: Number(__ENV.VUS || 1),
    duration: __ENV.DURATION || '30s',
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
        trending_duration: ['p(95)<10000'],
    },
};

const trendingTrend = new Trend('trending_duration');
const errorRate = new Rate('trending_error_rate');

export default function () {
    const user = tokens[(__VU - 1) % tokens.length];
    const cookies = { userAccessToken: user.token };

    const query = `page=${PAGE}&size=${SIZE}` + (EXCLUDE_MEDIA_ID ? `&excludeMediaId=${EXCLUDE_MEDIA_ID}` : '');
    const response = http.get(`${BASE_URL}/playlists/trending?${query}`, {
        cookies,
        tags: { api: 'trending' },
    });

    trendingTrend.add(response.timings.duration);
    errorRate.add(response.status !== 200);

    check(response, {
        'trending 200': (res) => res.status === 200,
    });

    if (THINK_TIME_SECONDS > 0) {
        sleep(THINK_TIME_SECONDS);
    }
}