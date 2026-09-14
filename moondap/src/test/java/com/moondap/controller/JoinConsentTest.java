package com.moondap.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.dto.MdUserDTO;
import com.moondap.service.StandardMdUserService;

/**
 * 회원가입 필수 동의의 서버 측 강제.
 *
 * <p>화면의 체크박스 검사는 개발자 도구로 폼을 고치면 그대로 우회된다. 동의 없이
 * 개인정보를 수집하면 개인정보보호법 위반이므로, 실제 차단은 컨트롤러가 해야 한다.
 * 이 테스트는 그 지점이 사라지지 않도록 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JoinConsentTest {

    @Autowired
    private MockMvc mockMvc;

    /** 가입이 실제로 수행됐는지만 보면 되므로 서비스는 대역으로 둔다(테스트 DB 가 없다). */
    @MockitoBean
    private StandardMdUserService standardMdUserService;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder joinRequest() {
        return post("/joinProc").with(csrf())
                .param("username", "tester01")
                .param("password", "Abcd1234!")
                .param("nickname", "테스터")
                .param("role", "ROLE_USER");
    }

    @Test
    @DisplayName("세 항목에 모두 동의하면 가입이 진행된다")
    void joinsWhenAllConsentsGiven() throws Exception {
        mockMvc.perform(joinRequest()
                        .param("agreeTerms", "true")
                        .param("agreePrivacy", "true")
                        .param("agreeAge", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/joinCompleteView"));

        verify(standardMdUserService).joinProc(any(MdUserDTO.class));
    }

    @Test
    @DisplayName("[회귀] 동의 파라미터가 아예 없으면 가입을 막는다")
    void rejectsWhenConsentParamsMissing() throws Exception {
        // 체크박스는 해제 시 파라미터 자체가 전송되지 않는다.
        // required=false + 기본 false 로 받지 않으면 400 이 나거나 검사가 통째로 빠진다.
        mockMvc.perform(joinRequest())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/joinViewAfterError"));

        verify(standardMdUserService, never()).joinProc(any());
    }

    @Test
    @DisplayName("[회귀] 한 항목이라도 빠지면 가입을 막는다")
    void rejectsWhenAnyConsentMissing() throws Exception {
        mockMvc.perform(joinRequest()
                        .param("agreePrivacy", "true")
                        .param("agreeAge", "true"))
                .andExpect(redirectedUrl("/joinViewAfterError"));

        mockMvc.perform(joinRequest()
                        .param("agreeTerms", "true")
                        .param("agreeAge", "true"))
                .andExpect(redirectedUrl("/joinViewAfterError"));

        // 만 14세 미만은 법정대리인 동의가 필요해 이 폼으로는 가입시킬 수 없다.
        mockMvc.perform(joinRequest()
                        .param("agreeTerms", "true")
                        .param("agreePrivacy", "true"))
                .andExpect(redirectedUrl("/joinViewAfterError"));

        verify(standardMdUserService, never()).joinProc(any());
    }

    @Test
    @DisplayName("[회귀] false 로 조작해 보내도 가입을 막는다")
    void rejectsForgedFalseValues() throws Exception {
        mockMvc.perform(joinRequest()
                        .param("agreeTerms", "false")
                        .param("agreePrivacy", "true")
                        .param("agreeAge", "true"))
                .andExpect(redirectedUrl("/joinViewAfterError"));

        verify(standardMdUserService, never()).joinProc(any());
    }

    @Test
    @DisplayName("가입 화면에 동의 항목 세 개가 렌더링된다")
    void rendersConsentCheckboxes() throws Exception {
        // Thymeleaf 표현식 오류는 컴파일이 아니라 렌더 시점에 드러난다.
        String html = mockMvc.perform(post("/joinView").with(csrf()).param("role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("name=\"agreeTerms\"")
                .contains("name=\"agreePrivacy\"")
                .contains("name=\"agreeAge\"")
                .contains("id=\"agreeAll\"")
                .contains("만 14세 이상입니다.");

        // 동의 항목이 체크된 채로 시작하면 '동의를 받았다'고 보기 어렵다.
        org.assertj.core.api.Assertions.assertThat(html)
                .doesNotContain("class=\"form-check-input consent-item\" type=\"checkbox\" id=\"agreeTerms\" name=\"agreeTerms\" value=\"true\" checked");
    }

    @Test
    @DisplayName("푸터에 쿠키 설정(동의 철회) 링크가 들어간다")
    void rendersCookieSettingsLink() throws Exception {
        String html = mockMvc.perform(post("/joinView").with(csrf()).param("role", "ROLE_USER"))
                .andReturn().getResponse().getContentAsString();

        // 애드센스 Privacy & messaging 가이드라인이 요구하는 재설정 경로다.
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("id=\"cookie-settings-link\"")
                .contains("showRevocationMessage")
                .contains("CONSENT_API_READY");

        // 동의 메시지를 받지 않는 지역에서는 죽은 링크가 되므로 기본은 숨김이어야 한다.
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("id=\"cookie-settings-wrap\" style=\"display: none;\"");
    }
}
