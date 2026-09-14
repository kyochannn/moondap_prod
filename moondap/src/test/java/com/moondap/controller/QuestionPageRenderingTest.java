package com.moondap.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.service.EgenTetoService;
import com.moondap.service.MdTestUserService;

/**
 * 질문지 화면.
 *
 * <p>쿠팡 파트너스 배너를 걷어내면서 분석 대기 모달의 HTML 을 손으로 지웠다. 태그를
 * 하나만 잘못 지워도 모달 전체가 깨지는데, 이 화면은 테스트를 끝까지 풀어야 열려서
 * 수동 확인이 번거롭다. 구조가 살아 있는지와 광고가 사라졌는지를 함께 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestUserService mdTestUserService;

    @MockitoBean
    private EgenTetoService egenTetoService;

    private MdTestDTO activeTest() {
        MdTestDTO test = new MdTestDTO();
        test.setId(1L);
        test.setTestKey("sample");
        test.setTitle("샘플 테스트");
        test.setDescription("설명");
        test.setStatus("active");
        test.setTestType("TYPE");

        MdTestQuestionDTO question = new MdTestQuestionDTO();
        question.setQuestionText("첫 번째 질문");
        test.setQuestions(List.of(question));
        test.setResults(List.of());
        return test;
    }

    @Test
    @DisplayName("심리테스트 질문지가 렌더링되고 분석 모달이 온전하다")
    void rendersTestQuestions() throws Exception {
        when(mdTestUserService.getFullTestData("sample")).thenReturn(activeTest());

        String html = mockMvc.perform(get("/test/sample/questions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"analysisModal\"");
        assertThat(html).contains("id=\"finalSubmitBtn\"");
        assertThat(html).contains("결과가 준비되었습니다");
    }

    @Test
    @DisplayName("에겐테토 질문지가 렌더링되고 분석 모달이 온전하다")
    void rendersEgenTetoQuestions() throws Exception {
        when(egenTetoService.getGenderCounts()).thenReturn(Map.of("maleCount", 1L, "femaleCount", 1L));

        String html = mockMvc.perform(get("/egenTeto/questions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"analysisModal\"");
        assertThat(html).contains("id=\"finalSubmitBtn\"");
    }

    @Test
    @DisplayName("[회귀] 질문지에 쿠팡 파트너스 광고가 남아 있지 않다")
    void hasNoCoupangAd() throws Exception {
        when(mdTestUserService.getFullTestData("sample")).thenReturn(activeTest());
        when(egenTetoService.getGenderCounts()).thenReturn(Map.of("maleCount", 1L, "femaleCount", 1L));

        for (String uri : List.of("/test/sample/questions", "/egenTeto/questions")) {
            String html = mockMvc.perform(get(uri)).andReturn().getResponse().getContentAsString();

            assertThat(html).doesNotContain("link.coupang.com");
            assertThat(html).doesNotContain("coupang-disclaimer");
            assertThat(html).doesNotContain("파트너스 활동의 일환");
            // 남은 외부 광고는 애드센스뿐이어야 한다.
            assertThat(html).doesNotContain("ad-slot");
        }
    }
}
