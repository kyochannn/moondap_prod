package com.moondap.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdUserDTO;
import com.moondap.service.VisitLogRetentionService;

/**
 * 접속 기록 파기 권한.
 *
 * <p>파기 대상은 IP 가 담긴 개인정보이고 되돌릴 수 없다. 그래서 ADMIN 권한만으로는
 * 부족하다고 보고 mdadmin 계정 하나로 좁혔다. 화면에서 버튼을 감추는 것만으로는
 * 주소를 아는 사람이 그대로 호출할 수 있으므로, 서버가 막는지를 여기서 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VisitLogPurgeAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisitLogRetentionService visitLogRetentionService;

    @Test
    @DisplayName("mdadmin 은 파기할 수 있다")
    void mdadminCanPurge() throws Exception {
        mockMvc.perform(post("/admin/stats/purge-logs").with(csrf()).with(user(principal("mdadmin", "ROLE_ADMIN"))))
                .andExpect(status().is3xxRedirection());

        verify(visitLogRetentionService).purgeExpired();
    }

    @Test
    @DisplayName("[회귀] mdadmin 이 아닌 관리자는 파기할 수 없다")
    void otherAdminCannotPurge() throws Exception {
        // 관리자 계정은 앞으로 늘어날 수 있다. ADMIN 이면 통과시키면 안 된다.
        mockMvc.perform(post("/admin/stats/purge-logs").with(csrf()).with(user(principal("otheradmin", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verify(visitLogRetentionService, never()).purgeExpired();
    }

    @Test
    @DisplayName("일반 사용자는 파기할 수 없다")
    void normalUserCannotPurge() throws Exception {
        // 계정명이 같아도 권한이 없으면 막혀야 한다.
        mockMvc.perform(post("/admin/stats/purge-logs").with(csrf()).with(user(principal("mdadmin", "ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(visitLogRetentionService, never()).purgeExpired();
    }

    @Test
    @DisplayName("CSRF 토큰 없이는 파기할 수 없다")
    void csrfRequired() throws Exception {
        mockMvc.perform(post("/admin/stats/purge-logs").with(user(principal("mdadmin", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verify(visitLogRetentionService, never()).purgeExpired();
    }

    /**
     * 실제 로그인과 같은 형태의 인증 주체를 만든다.
     *
     * <p>{@code @WithMockUser} 를 쓰면 principal 이 스프링 기본 User 라
     * 공통 헤더의 {@code #authentication.principal.user.profileImage} 에서 터진다.
     * 권한 판정이 아니라 오류 화면 렌더링에서 깨지는 것이라, 실제와 같은 타입을 쓴다.
     */
    private PrincipalDetails principal(String username, String role) {
        MdUserDTO user = new MdUserDTO();
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setProfileImage("default-profile-img.svg");
        return new PrincipalDetails(user);
    }
}
