package com.ott.common.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeContractTest {

    @Test
    void representativeErrorCodeStringsAndMessagesMatchExistingContract() {
        assertThat(ErrorCode.INVALID_INPUT.getCode()).isEqualTo("C001");
        assertThat(ErrorCode.INVALID_INPUT.getMessage()).isEqualTo("입력값이 올바르지 않습니다");
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo("A001");
        assertThat(ErrorCode.UNAUTHORIZED.getMessage()).isEqualTo("인증이 필요합니다");
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo("C999");
        assertThat(ErrorCode.INTERNAL_ERROR.getMessage()).isEqualTo("서버 오류가 발생했습니다");
    }
}
