package com.moondap.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.moondap.dto.TestHistoryDTO;
import com.moondap.service.TestHistoryService;

/**
 * 보관함 썸네일이 '파일이 사라진 경우'까지 버티는지 확인한다.
 *
 * <p>보관함은 결과 이미지의 파일명을 그 시점 값으로 복사해 둔다. 그런데 콘텐츠를
 * 수정하면 더 이상 쓰이지 않는 이미지 파일은 서버에서 삭제된다
 * ({@code MdTestAdminService.updateTest}). 그래서 "파일명은 있는데 파일은 없는"
 * 상태가 생기고, {@code th:if} 로는 걸러지지 않아 깨진 이미지가 남는다.
 *
 * <p>화면은 onerror 로 아이콘으로 되돌리는데, 이런 템플릿 표현식 오타는 컴파일에서
 * 걸리지 않고 렌더링 시점에야 드러난다. 그래서 렌더된 HTML 로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoryThumbRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestHistoryService testHistoryService;

    private static TestHistoryDTO history(String resultImage) {
        TestHistoryDTO dto = new TestHistoryDTO();
        dto.setNo(1L);
        dto.setTestKey("T-260426-2");
        dto.setTestTitle("번아웃 진단 테스트");
        dto.setResultCode("다 타버린 하얀 재");
        dto.setResultTitle("다 타버린 하얀 재");
        dto.setResultImage(resultImage);
        dto.setScore(90);
        return dto;
    }

    @Test
    @DisplayName("이미지가 있으면 onerror 대체가 걸린 img 와 숨겨진 아이콘이 함께 나온다")
    void rendersFallbackHandlerAlongsideImage() throws Exception {
        when(testHistoryService.list(anyInt())).thenReturn(List.of(history("abc.png")));
        when(testHistoryService.claimAnonymousHistory()).thenReturn(0);

        mockMvc.perform(get("/my/results"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/uploads/abc.png")))
                // 파일이 사라졌을 때 아이콘으로 되돌릴 손잡이
                .andExpect(content().string(containsString(
                        "onerror=\"this.hidden = true; if (this.nextElementSibling)")))
                // 아이콘은 렌더는 되어 있고 숨겨져만 있어야 onerror 가 꺼낼 수 있다.
                // (단순히 "hidden" 만 찾으면 CSS 의 overflow: hidden 에도 걸려 검증이 무의미해진다)
                .andExpect(content().string(containsString(
                        "history-thumb-fallback\" hidden=\"hidden\"")));
    }

    @Test
    @DisplayName("이미지가 없으면 img 없이 아이콘만 보인다")
    void rendersIconOnlyWhenImageMissing() throws Exception {
        when(testHistoryService.list(anyInt())).thenReturn(List.of(history(null)));
        when(testHistoryService.claimAnonymousHistory()).thenReturn(0);

        mockMvc.perform(get("/my/results"))
                .andExpect(status().isOk())
                // 숨김 없이 아이콘만 나온다
                .andExpect(content().string(containsString(
                        "<i class=\"bi bi-journal-text history-thumb-fallback\"></i>")))
                .andExpect(content().string(not(containsString("/uploads/"))));
    }
}
