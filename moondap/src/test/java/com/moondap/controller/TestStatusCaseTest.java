package com.moondap.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
 * 콘텐츠 공개 여부 판정이 status 의 대소문자에 흔들리지 않는지 확인한다.
 *
 * <p>[회귀] 매퍼의 {@code WHERE status = 'active'} 는 MySQL 콜레이션상 대소문자를
 * 구분하지 않아 'ACTIVE' 행도 목록에 포함시킨다. 그런데 컨트롤러는
 * {@code "active".equals(...)} 로 비교했기 때문에, 메인 화면 카드에는 보이는 테스트를
 * 눌렀을 때만 "접근 권한이 없습니다" 오류 페이지가 떴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestStatusCaseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestUserService mdTestUserService;

    @Test
    @DisplayName("[회귀] status 가 'ACTIVE' 여도 상세 페이지가 열린다")
    void opensIntroWhenStatusIsUppercase() throws Exception {
        BDDMockito.given(mdTestUserService.getFullTestData("love-style"))
                .willReturn(test("ACTIVE"));

        mockMvc.perform(get("/test/love-style"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 상태가 아니면 여전히 막힌다")
    void blocksDraft() throws Exception {
        BDDMockito.given(mdTestUserService.getFullTestData("love-style"))
                .willReturn(test("draft"));

        // 로그인하지 않은 요청이므로 작성자·관리자 어느 쪽도 아니다.
        mockMvc.perform(get("/test/love-style"))
                .andExpect(status().isBadRequest());
    }

    private MdTestDTO test(String status) {
        MdTestDTO dto = new MdTestDTO();
        dto.setId(1L);
        dto.setTestKey("love-style");
        dto.setTitle("연애 성향 테스트");
        dto.setDescription("나는 어떤 연애를 하는 사람일까?");
        dto.setCategoryDisplayName("연애");
        dto.setEstimatedTime(3);
        dto.setPlayCount(0);
        dto.setStatus(status);
        dto.setCreatedBy("someone-else");
        dto.setQuestions(List.of());
        dto.setResults(List.of());
        return dto;
    }
}
