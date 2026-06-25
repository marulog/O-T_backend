/**
 * 홈 화면 스모크 테스트
 *
 * 실행 예시:
 *   k6 run .\k6\scripts\smoke.js
 */

import { runHomeScenario } from './shared/home-test.js';

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
    },
};

export default runHomeScenario;
