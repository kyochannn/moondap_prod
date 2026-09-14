package com.moondap.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.service.MdTestUserService;

/**
 * 측정 ID 가 설정됐을 때 GA4 스니펫이 실제로 페이지에 실리는지 확인한다.
 *
 * <p>스니펫은 {@code main_layout.html} 안에서 {@code @environment.getProperty(...)} 로
 * 값을 읽는다. 이 표현식은 컴파일 단계에서 검증되지 않기 때문에, 오타가 나거나
 * Thymeleaf 의 속성 처리 우선순위(th:if 가 th:with 보다 먼저 평가된다)를 잘못 쓰면
 * 예외 없이 조용히 빈 문자열이 되어 추적이 통째로 사라진다. 그런 실패는 몇 주 뒤
 * "왜 데이터가 안 쌓이지" 로만 드러나므로 렌더 결과로 고정해 둔다.
 *
 * <p>렌더 대상으로 404 화면을 쓰는 것은 이 화면이 서비스 의존성 없이 main_layout 을
 * 그대로 타는 가장 단순한 경로이기 때문이다. 검증하려는 것은 레이아웃이지 404 가 아니다.
 */
@SpringBootTest(properties = "analytics.ga4-id=G-TEST123456")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Ga4SnippetRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestUserService mdTestUserService;

    @Test
    @DisplayName("측정 ID 가 있으면 gtag 스크립트와 config 호출이 함께 나간다")
    void rendersSnippetWhenIdConfigured() throws Exception {
        String html = mockMvc.perform(get("/terms"))
                .andExpect(content().string(Matchers.containsString(
                        "https://www.googletagmanager.com/gtag/js?id=G-TEST123456")))
                .andReturn().getResponse().getContentAsString();

        // 로더만 있고 config 가 빠지면 페이지뷰가 한 건도 잡히지 않는다.
        // 따옴표 종류는 고정하지 않는다 — th:inline 이 값을 JS 리터럴로 직렬화하므로
        // 작은따옴표/큰따옴표는 Thymeleaf 구현 사정이지 우리가 보장할 계약이 아니다.
        org.assertj.core.api.Assertions.assertThat(html)
                .as("gtag config 호출이 측정 ID 와 함께 나가야 한다")
                .containsPattern("gtag\\(\\s*['\"]config['\"]\\s*,\\s*['\"]G-TEST123456['\"]\\s*\\)");
    }

    @Test
    @DisplayName("이벤트 추적 모듈이 공유 스크립트보다 먼저 로드된다")
    void loadsAnalyticsBeforeShareService() throws Exception {
        String html = mockMvc.perform(get("/terms"))
                .andReturn().getResponse().getContentAsString();

        // share-service.js 는 mdWithRef/mdTrackShare 를 호출한다. 순서가 뒤집혀도
        // typeof 검사 덕분에 예외는 안 나지만 공유 집계만 조용히 빠진다.
        int analytics = html.indexOf("/assets/js/analytics.js");
        int share = html.indexOf("/assets/js/share-service.js");

        org.assertj.core.api.Assertions.assertThat(analytics)
                .as("analytics.js 가 로드되지 않았다")
                .isGreaterThan(-1);
        org.assertj.core.api.Assertions.assertThat(share)
                .as("share-service.js 가 로드되지 않았다")
                .isGreaterThan(-1);
        org.assertj.core.api.Assertions.assertThat(analytics)
                .as("analytics.js 는 share-service.js 보다 먼저 와야 한다")
                .isLessThan(share);
    }
}
