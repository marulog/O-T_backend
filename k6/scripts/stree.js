/**
 * 홈 화면 스트레스 테스트
 *
 * 실행 예시:
 *   k6 run .\k6\scripts\stress.js
 */

import { createHomeThresholds, runHomeScenario } from './shared/home-test.js';

export const options = {
    scenarios: {
        home_stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '30s', target: 40 },
                { duration: '30s', target: 60 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: createHomeThresholds({
        http_req_failed: ['rate<0.05'],
    }),
};

export default runHomeScenario;
