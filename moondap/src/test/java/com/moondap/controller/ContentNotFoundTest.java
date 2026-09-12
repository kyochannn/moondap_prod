package com.moondap.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.service.MdTestUserService;

/**
 * 없는 콘텐츠 주소가 404 로 답하는지 확인한다.
 *
 * <p>[회귀] 없는 테스트 주소는 메인으로 302 리다이렉트됐고, 밸런스 게임 쪽은 400 을 냈다.
 * 둘 다 크롤러에게는 "이 URL 은 살아 있다"로 읽혀(soft 404), 삭제한 콘텐츠의 주소가
 * 색인에 남는다. 사이트맵을 동적으로 만들기 시작한 뒤로는 더 문제가 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentNotFoundTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestUserService mdTestUserService;

    @Test
    @DisplayName("[회귀] 없는 테스트는 메인으로 보내지 않고 404 를 준다")
    void missingTestReturnsNotFound() throws Exception {
        BDDMockito.given(mdTestUserService.getFullTestData("no-such-key")).willReturn(null);

        mockMvc.perform(get("/test/no-such-key"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("요청하신 테스트를 찾을 수 없습니다.")));
    }

    @Test
    @DisplayName("질문·결과 페이지도 같은 기준으로 404 다")
    void missingTestSubPagesReturnNotFound() throws Exception {
        BDDMockito.given(mdTestUserService.getFullTestData("no-such-key")).willReturn(null);

        mockMvc.perform(get("/test/no-such-key/questions")).andExpect(status().isNotFound());
        mockMvc.perform(get("/test/no-such-key/result")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AJAX 요청에는 404 JSON 을 준다")
    void ajaxGetsJson() throws Exception {
        BDDMockito.given(mdTestUserService.getFullTestData("no-such-key")).willReturn(null);

        mockMvc.perform(get("/test/no-such-key").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("\"status\":404")));
    }
}
