/**
 * 홈 화면 부하 테스트
 *
 * 실행 예시:
 *   k6 run .\k6\scripts\load.js
 */

import { createHomeThresholds, runHomeScenario } from './shared/home-test.js';

export const options = {
    scenarios: {
        home_load: {
            executor: 'ramping-vus',
            startVUs: 0, // 동시 접속자 0명
            stages: [
                { duration: '30s', target: 50  },   // warm-up
                { duration: '2m',  target: 50  },   // 50명 Baseline 측정
                { duration: '30s', target: 100 },   // 증가
                { duration: '2m',  target: 100 },   // 100명 Target 측정
                { duration: '30s', target: 0   },   // cool-down
            ],
        },
    },
    thresholds: createHomeThresholds(),
};

export default runHomeScenario;
