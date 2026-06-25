/**
 * 부하 테스트용 토큰 사전 발급 스크립트
 *
 * 실행 방법(프로젝트 루트 기준):
 *   k6 run .\k6\setup\generate-tokens.js
 *
 * 환경 변수:
 *   - BASE_URL: API 서버 주소 (기본값: http://localhost:8080)
 *   - START_MEMBER_ID: 발급 시작 memberId (기본값: 13)
 *   - END_MEMBER_ID: 발급 종료 memberId (기본값: 100)
 *   - USER_COUNT: END_MEMBER_ID와 동일하게 동작하는 하위 호환 변수
 *
 * 결과:
 *   console output 파일에 JSON 배열 저장
 *   → k6\data\tokens.json 파일을 home.js에서 로드해서 사용
 *
 * 사전 조건:
 *   - api-user 서버 실행 중 (기본 포트 8080)
 *   - DB에 시드 유저 데이터 삽입 완료
 *   - spring.profiles.active != prod (TestAuthController 활성화 필요)
 *
 * PowerShell 예시:
 *   $env:BASE_URL="http://localhost:8080"
 *   $env:START_MEMBER_ID="13"
 *   $env:END_MEMBER_ID="100"
 *   k6 run --logformat raw --console-output .\k6\data\tokens.json .\k6\setup\generate-tokens.js
 */

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBER_ID_START = Number.parseInt(__ENV.START_MEMBER_ID || '13', 10);
const MEMBER_ID_END = Number.parseInt(__ENV.END_MEMBER_ID || __ENV.USER_COUNT || '100', 10);

if (Number.isNaN(MEMBER_ID_START) || Number.isNaN(MEMBER_ID_END)) {
    throw new Error('START_MEMBER_ID/END_MEMBER_ID(USER_COUNT)는 숫자여야 합니다.');
}

if (MEMBER_ID_START > MEMBER_ID_END) {
    throw new Error('START_MEMBER_ID는 END_MEMBER_ID보다 클 수 없습니다.');
}

export const options = {
    // 토큰 발급은 순차 실행 (단일 VU)
    vus: 1,
    iterations: 1,
};

export default function () {
    const tokens = [];
    const failures = [];

    for (let memberId = MEMBER_ID_START; memberId <= MEMBER_ID_END; memberId++) {
        const res = http.post(`${BASE_URL}/test/auth/token/${memberId}`);

        const ok = check(res, {
            [`memberId=${memberId} 토큰 발급 성공`]: (r) => r.status === 200,
        });

        if (ok) {
            try {
                const body = JSON.parse(res.body);

                if (!body.accessToken) {
                    throw new Error('accessToken 필드가 없습니다.');
                }

                tokens.push({ memberId, token: body.accessToken });
            } catch (error) {
                failures.push(`memberId=${memberId} 응답 파싱 실패: ${error.message}`);
            }
        } else {
            failures.push(`memberId=${memberId} 토큰 발급 실패: ${res.status} ${res.body}`);
        }
    }

    if (failures.length > 0) {
        throw new Error(
            `토큰 발급 실패 ${failures.length}건\n${failures.slice(0, 10).join('\n')}`,
        );
    }

    // --console-output 파일에 JSON 배열 한 줄만 남기기 위해 로그를 하나만 출력한다.
    console.log(JSON.stringify(tokens));
}
