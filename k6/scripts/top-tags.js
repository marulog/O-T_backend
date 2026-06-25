// k6 run .\k6\scripts\top-tags.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKENS_PATH = __ENV.TOKENS_PATH || '../data/tokens.json';
const THINK_TIME_SECONDS = Number.parseFloat(__ENV.THINK_TIME_SECONDS || '1');
const PAGE = Number.parseInt(__ENV.PAGE || '0', 10);
const SIZE = Number.parseInt(__ENV.SIZE || '20', 10);
const TOP_TAG_INDEXES = parseIndexes(__ENV.TOP_TAG_INDEXES || __ENV.INDEX || '0,1,2');

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
        top_tag_duration: ['p(95)<10000'],
    },
};

const topTagTrend = new Trend('top_tag_duration');
const errorRate = new Rate('top_tag_error_rate');

export default function () {
    const user = tokens[(__VU - 1) % tokens.length];
    const cookies = { userAccessToken: user.token };

    for (const index of TOP_TAG_INDEXES) {
        const response = http.get(`${BASE_URL}/playlists/tags/top?index=${index}&page=${PAGE}&size=${SIZE}`, {
            cookies,
            tags: { api: 'top_tag', index: String(index) },
        });

        topTagTrend.add(response.timings.duration, { index: String(index) });
        errorRate.add(response.status !== 200, { index: String(index) });

        check(response, {
            [`top_tag[${index}] 200`]: (res) => res.status === 200,
        }, { index: String(index) });
    }

    if (THINK_TIME_SECONDS > 0) {
        sleep(THINK_TIME_SECONDS);
    }
}

function parseIndexes(value) {
    const indexes = value
        .split(',')
        .map((item) => Number.parseInt(item.trim(), 10))
        .filter((item) => !Number.isNaN(item));

    if (indexes.length === 0) {
        throw new Error('TOP_TAG_INDEXES 값이 비어 있습니다.');
    }

    for (const index of indexes) {
        if (index < 0 || index > 2) {
            throw new Error('TOP_TAG_INDEXES는 0, 1, 2만 사용할 수 있습니다.');
        }
    }

    return indexes;
}
