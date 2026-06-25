// k6 run .\k6\scripts\short-form.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKENS_PATH = __ENV.TOKENS_PATH || '../data/tokens.json';
const THINK_TIME_SECONDS = Number.parseFloat(__ENV.THINK_TIME_SECONDS || '1');

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
        short_form_duration: ['p(95)<10000'],
    },
};

const shortFormTrend = new Trend('short_form_duration');
const errorRate = new Rate('short_form_error_rate');

export default function () {
    const user = tokens[(__VU - 1) % tokens.length];
    const cookies = { userAccessToken: user.token };

    const response = http.get(`${BASE_URL}/short-forms?page=0&size=10`, {
        cookies,
        tags: { api: 'short_form' },
    });

    shortFormTrend.add(response.timings.duration);
    errorRate.add(response.status !== 200);

    check(response, {
        'short_form 200': (res) => res.status === 200,
    });

    if (THINK_TIME_SECONDS > 0) {
        sleep(THINK_TIME_SECONDS);
    }
}
