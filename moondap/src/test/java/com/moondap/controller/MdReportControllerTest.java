package com.moondap.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdReportDTO;
import com.moondap.dto.MdUserDTO;
import com.moondap.service.MdReportService;

/**
 * 신고 접수·관리 경로.
 *
 * <p>접수는 비로그인 이용자에게 열려 있어야 하고, 관리는 관리자에게만 열려 있어야 한다.
 * 둘 중 하나라도 어긋나면 기능이 무용지물이 되거나 아무나 신고를 지울 수 있게 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MdReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdReportService mdReportService;

    private PrincipalDetails principal(String username, String role) {
        MdUserDTO user = new MdUserDTO();
        user.setUsername(username);
        user.setPassword("pw");
        user.setNickname("닉");
        user.setRole(role);
        return new PrincipalDetails(user);
    }

    private String body(String targetType, String targetId, String reason) {
        return """
               {"targetType":"%s","targetId":"%s","reasonCode":"%s","detail":"내용"}
               """.formatted(targetType, targetId, reason);
    }

    @Test
    @DisplayName("비로그인 이용자도 신고를 접수할 수 있다")
    void anonymousCanSubmitReport() throws Exception {
        // 로그인을 요구하면 대부분의 방문자가 신고하지 못해 기능이 있으나 마나가 된다.
        mockMvc.perform(post("/report").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("COMMENT", "12", "ABUSE")))
                .andExpect(status().isOk());

        verify(mdReportService).report(any(), anyString());
    }

    @Test
    @DisplayName("신고 접수 시 익명 식별 쿠키를 내려준다")
    void issuesAnonymousCookie() throws Exception {
        var response = mockMvc.perform(post("/report").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("COMMENT", "12", "ABUSE")))
                .andReturn().getResponse();

        // 이 쿠키가 없으면 같은 사람의 중복 신고를 막을 수 없다.
        assertThat(response.getCookie("md_anon")).isNotNull();
    }

    @Test
    @DisplayName("[회귀] 사유가 비면 접수하지 않는다")
    void rejectsMissingReason() throws Exception {
        mockMvc.perform(post("/report").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"COMMENT\",\"targetId\":\"12\"}"))
                .andExpect(status().isBadRequest());

        verify(mdReportService, never()).report(any(), anyString());
    }

    @Test
    @DisplayName("[회귀] 관리 화면은 일반 사용자에게 열리지 않는다")
    void adminPageIsRestricted() throws Exception {
        mockMvc.perform(get("/admin/report/list").with(user(principal("tester", "ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 신고 목록을 볼 수 있다")
    void adminCanViewReports() throws Exception {
        MdReportDTO report = new MdReportDTO();
        report.setNo(1L);
        report.setTargetType("COMMENT");
        report.setTargetId("12");
        report.setReasonCode("ABUSE");
        report.setTargetSnapshot("작성자: 문제 댓글");
        report.setStatus("PENDING");
        report.setReportCount(3);

        when(mdReportService.getReports(anyString(), anyInt(), anyInt())).thenReturn(List.of(report));
        when(mdReportService.countReports(anyString())).thenReturn(1);
        when(mdReportService.countPending()).thenReturn(1);

        String html = mockMvc.perform(get("/admin/report/list")
                        .with(user(principal("admin", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("신고 관리");
        assertThat(html).contains("작성자: 문제 댓글");
        assertThat(html).contains("욕설·비방");
        assertThat(html).contains("3건");
    }

    @Test
    @DisplayName("[회귀] 관리 화면은 검색 결과에 노출되지 않는다")
    void adminPageIsNoindex() throws Exception {
        when(mdReportService.getReports(anyString(), anyInt(), anyInt())).thenReturn(List.of());

        String html = mockMvc.perform(get("/admin/report/list")
                        .with(user(principal("admin", "ROLE_ADMIN"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<meta name=\"robots\" content=\"noindex, follow\"");
    }

    @Test
    @DisplayName("관리자는 신고를 처리 상태로 바꿀 수 있다")
    void adminCanUpdateStatus() throws Exception {
        mockMvc.perform(post("/admin/report/1/status").with(csrf())
                        .with(user(principal("admin", "ROLE_ADMIN")))
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk());

        verify(mdReportService).updateStatus(1L, "RESOLVED");
    }

    @Test
    @DisplayName("[회귀] 일반 사용자는 신고를 처리할 수 없다")
    void userCannotUpdateStatus() throws Exception {
        mockMvc.perform(post("/admin/report/1/status").with(csrf())
                        .with(user(principal("tester", "ROLE_USER")))
                        .param("status", "RESOLVED"))
                .andExpect(status().isForbidden());

        verify(mdReportService, never()).updateStatus(any(), anyString());
    }
}
