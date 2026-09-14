package com.moondap.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.service.MdTestUserService;
import com.moondap.service.TestHistoryService;

/**
 * 결과 화면의 완료 집계가 '실제 응시'에만 붙는지 확인한다.
 *
 * <p>이 화면에 닿는 경로는 셋이다. 방금 응시한 POST, 공유 링크를 타고 온 GET,
 * 검색으로 들어온 GET. 뒤 둘은 남의 결과를 구경하는 것이지 응시가 아니다.
 * 셋을 구분하지 않고 페이지 로드마다 {@code test_complete} 를 보내면, 공유가 잘 될수록
 * 완료 수가 부풀어 {@code test_start} 대비 완료율이 100% 를 넘는 값이 나온다.
 * 하필 그 지표가 "문항이 너무 길어 이탈하는가" 를 판단하는 근거라, 지표가 망가지면
 * 판단도 같이 망가진다.
 *
 * <p>구분 기준으로 새 플래그를 만들지 않고 {@code justCompleted} 를 재사용한다.
 * 진행 기록(localStorage) 삭제가 이미 같은 질문("방금 푼 것이 맞는가")에 답하고 있어서,
 * 둘이 갈라지면 오히려 어느 쪽이 맞는지 알 수 없게 된다.
 *
 * <p>인라인 표현식이 깨졌는지도 여기서 함께 걸린다. 결과 화면 스크립트는 Thymeleaf
 * 인라인({@code /*[[${...}]]*&#47;})으로 값을 심는데, 문법이 틀리면 컴파일이 아니라
 * 렌더 시점에 터진다. 사이트에서 가장 중요한 화면이 500 으로 떨어지는 방식이다.
 */
@SpringBootTest(properties = "analytics.ga4-id=G-TEST123456")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestCompleteTrackingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestUserService mdTestUserService;

    @MockitoBean
    private TestHistoryService testHistoryService;

    private MdTestDTO activeTest() {
        MdTestDTO test = new MdTestDTO();
        test.setId(1L);
        test.setTestKey("sample");
        test.setTitle("샘플 테스트");
        test.setDescription("설명");
        test.setStatus("active");
        test.setTestType("TYPE");
        test.setQuestions(List.of());

        MdTestResultDTO result = new MdTestResultDTO();
        result.setId(3L);
        result.setTestId(1L);
        result.setResultTitle("햇살형");
        result.setResultContent("결과 본문");
        test.setResults(List.of(result));
        return test;
    }

    @Test
    @DisplayName("방금 응시를 마친 화면(POST)은 test_complete 를 보낸다")
    void sendsCompleteOnActualSubmission() throws Exception {
        when(mdTestUserService.getFullTestData("sample")).thenReturn(activeTest());

        // 컨트롤러는 광고 단계를 건너뛴 POST 를 소개 화면으로 되돌린다(AD_VERIFIED 세션 검사).
        // 여기서 검증하려는 것은 그 방어가 아니라 그 뒤에 오는 결과 화면이므로 통과시킨다.
        String html = mockMvc.perform(post("/test/sample/result")
                        .param("resultCode", "3")
                        .sessionAttr("AD_VERIFIED", Boolean.TRUE)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("test_complete");
        assertThat(html).contains("햇살형");
    }

    @Test
    @DisplayName("[회귀] 공유 링크로 열람한 화면(GET)은 test_complete 를 보내지 않는다")
    void doesNotSendCompleteOnSharedLink() throws Exception {
        when(mdTestUserService.getFullTestData("sample")).thenReturn(activeTest());

        String html = mockMvc.perform(get("/test/sample/result").param("resultCode", "3"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 결과 자체는 보여야 한다. 보내지 않는 것은 완료 집계뿐이다.
        assertThat(html).contains("햇살형");
        assertThat(html).doesNotContain("test_complete");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
