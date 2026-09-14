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
 * 측정 ID 가 비어 있으면 GA4 스니펫을 아예 내보내지 않는지 확인한다.
 *
 * <p>이게 깨지면 개발자 머신과 CI 에서 발생한 페이지뷰·이벤트가 운영 통계에 그대로
 * 섞인다. 한 번 섞인 데이터는 GA4 에서 소급해 걷어낼 수 없고, 바로 그 수치를 근거로
 * 콘텐츠와 유입 전략을 판단하게 되므로 오염이 판단까지 오염시킨다.
 *
 * <p>{@code analytics.ga4-id} 를 빈 값으로 명시하는 이유는 개발자 머신에
 * {@code GA4_MEASUREMENT_ID} 환경변수가 설정돼 있어도 결과가 흔들리지 않게 하기
 * 위해서다. 이 저장소가 테스트를 환경변수에서 떼어놓는 것과 같은 이유다.
 */
@SpringBootTest(properties = "analytics.ga4-id=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Ga4SnippetDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestUserService mdTestUserService;

    @Test
    @DisplayName("측정 ID 가 비면 gtag 스니펫이 렌더되지 않는다")
    void omitsSnippetWhenIdBlank() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("googletagmanager.com/gtag/js"))))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("gtag('config'"))));
    }

    @Test
    @DisplayName("스니펫이 없어도 추적 모듈은 실려 화면 코드가 죽지 않는다")
    void stillLoadsAnalyticsModule() throws Exception {
        // 화면 코드는 mdTrack 을 조건 없이 부른다. 스니펫이 빠진 환경에서 이 파일까지
        // 빠지면 ReferenceError 로 그 뒤 스크립트가 통째로 멈춘다.
        mockMvc.perform(get("/terms"))
                .andExpect(content().string(Matchers.containsString("/assets/js/analytics.js")));
    }
}
