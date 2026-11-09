package com.moondap.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.moondap.service.MdCalendarEventService;

/**
 * 관리자 캘린더 화면의 접근 제어.
 *
 * <p>관리자 2계정만 보는 화면이라도 서버가 막지 않으면 주소를 아는 사람이 그대로
 * 들어올 수 있다. 화면틀뿐인 1차 단계에서 접근 제어부터 고정해 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MdCalendarAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdCalendarEventService calendarEventService;

    @Test
    @DisplayName("ROLE_ADMIN 은 일정을 생성할 수 있다")
    void adminCanCreateEvent() throws Exception {
        mockMvc.perform(post("/admin/calendar/events/create")
                        .with(csrf())
                        .with(user(principal("mdadmin", "ROLE_ADMIN")))
                        .param("title", "회의")
                        .param("startAt", "2026-01-10")
                        .param("endAt", "2026-01-10"))
                .andExpect(status().isOk());

        verify(calendarEventService).createEvent(any(), anyString());
    }

    @Test
    @DisplayName("ROLE_USER 는 일정을 생성할 수 없다")
    void normalUserCannotCreateEvent() throws Exception {
        mockMvc.perform(post("/admin/calendar/events/create")
                        .with(csrf())
                        .with(user(principal("mduser", "ROLE_USER")))
                        .param("title", "회의")
                        .param("startAt", "2026-01-10")
                        .param("endAt", "2026-01-10"))
                .andExpect(status().isForbidden());

        verify(calendarEventService, never()).createEvent(any(), anyString());
    }

    @Test
    @DisplayName("ROLE_ADMIN 은 드래그로 일정을 옮길 수 있다")
    void adminCanRescheduleEvent() throws Exception {
        mockMvc.perform(post("/admin/calendar/events/1/reschedule")
                        .with(csrf())
                        .with(user(principal("mdadmin", "ROLE_ADMIN")))
                        .param("startAt", "2026-01-11")
                        .param("endAt", "2026-01-11"))
                .andExpect(status().isOk());

        verify(calendarEventService).rescheduleEvent(anyLong(), any(), any());
    }

    @Test
    @DisplayName("ROLE_USER 는 드래그로 일정을 옮길 수 없다")
    void normalUserCannotRescheduleEvent() throws Exception {
        mockMvc.perform(post("/admin/calendar/events/1/reschedule")
                        .with(csrf())
                        .with(user(principal("mduser", "ROLE_USER")))
                        .param("startAt", "2026-01-11")
                        .param("endAt", "2026-01-11"))
                .andExpect(status().isForbidden());

        verify(calendarEventService, never()).rescheduleEvent(anyLong(), any(), any());
    }

    @Test
    @DisplayName("ROLE_ADMIN 은 캘린더 화면에 들어갈 수 있다")
    void adminCanEnter() throws Exception {
        mockMvc.perform(get("/admin/calendar").with(user(principal("mdadmin", "ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE_USER 는 캘린더 화면에 들어갈 수 없다")
    void normalUserCannotEnter() throws Exception {
        mockMvc.perform(get("/admin/calendar").with(user(principal("mduser", "ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인하지 않으면 로그인 화면으로 보낸다")
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/calendar"))
                .andExpect(status().is3xxRedirection());
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
