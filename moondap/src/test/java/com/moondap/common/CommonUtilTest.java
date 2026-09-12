package com.moondap.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 클라이언트 IP 판별 테스트.
 *
 * <p>이 값은 투표 중복 방지와 일별 방문자 집계의 기준이다.
 * 클라이언트가 정할 수 있는 값을 쓰면 두 기능이 모두 무력화된다.
 */
class CommonUtilTest {

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        return request;
    }

    @Test
    @DisplayName("[회귀] X-Forwarded-For 를 위조해도 무시한다")
    void ignoresSpoofedForwardedFor() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        // 이전 구현은 이 헤더의 첫 값을 그대로 썼다.
        // 헤더만 바꿔 보내면 매 요청이 다른 IP 로 보여 중복 투표가 무제한 가능했다.
        assertThat(CommonUtil.getClientIp(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("[회귀] 다른 프록시 헤더들도 신뢰하지 않는다")
    void ignoresOtherProxyHeaders() {
        MockHttpServletRequest request = request();
        request.addHeader("Proxy-Client-IP", "5.6.7.8");
        request.addHeader("WL-Proxy-Client-IP", "5.6.7.8");
        request.addHeader("HTTP_CLIENT_IP", "5.6.7.8");
        request.addHeader("HTTP_X_FORWARDED_FOR", "5.6.7.8");

        assertThat(CommonUtil.getClientIp(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("헤더가 없으면 접속 주소를 그대로 쓴다")
    void usesRemoteAddr() {
        assertThat(CommonUtil.getClientIp(request())).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("접속 주소를 못 구하면 unknown 으로 처리한다")
    void fallsBackWhenUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        // null 이 그대로 흘러가면 voter_key 가 "ip:null" 이 되어
        // 모든 익명 사용자가 한 사람으로 묶인다.
        assertThat(CommonUtil.getClientIp(request)).isEqualTo("unknown");
    }
}
