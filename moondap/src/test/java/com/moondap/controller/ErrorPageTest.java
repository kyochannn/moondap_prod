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

import com.moondap.dto.MdTestDTO;
import com.moondap.service.MdTestUserService;

/**
 * 오류 화면이 실제 상태 코드와 사유를 보여주는지 확인한다.
 *
 * <p>error/500 템플릿은 500 전용이 아니라 400 응답에서도 쓰인다. 코드가 "500" 으로
 * 고정돼 있고 예외에 담긴 메시지도 출력하지 않아서, 입력·권한 문제인데 서버 장애처럼
 * 보였고 원인을 찾는 데 시간이 걸렸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestUserService mdTestUserService;

    @Test
    @DisplayName("[회귀] 400 응답 화면에는 500 이 아니라 400 이 보인다")
    void showsActualStatusCode() throws Exception {
        BDDMockito.given(mdTestUserService.getFullTestData("draft-test"))
                .willReturn(draftTest());

        mockMvc.perform(get("/test/draft-test"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("<div class=\"error-code\">400</div>")))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("<div class=\"error-code\">500</div>"))));
    }

    @Test
    @DisplayName("[회귀] 사용자에게 보여줄 메시지가 화면에 출력된다")
    void showsUserMessage() throws Exception {
        BDDMockito.given(mdTestUserService.getFullTestData("draft-test"))
                .willReturn(draftTest());

        mockMvc.perform(get("/test/draft-test"))
                .andExpect(content().string(Matchers.containsString("해당 테스트에 접근할 권한이 없습니다.")));
    }

    /** 비공개 상태라 익명 사용자에게는 UserMessageException 이 발생한다. */
    private MdTestDTO draftTest() {
        MdTestDTO dto = new MdTestDTO();
        dto.setId(1L);
        dto.setTestKey("draft-test");
        dto.setTitle("작성 중인 테스트");
        dto.setStatus("draft");
        dto.setCreatedBy("someone-else");
        return dto;
    }
}
