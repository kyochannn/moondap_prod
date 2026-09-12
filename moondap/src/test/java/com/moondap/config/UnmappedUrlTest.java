package com.moondap.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 없는 주소와 보호된 주소의 응답을 구분한다.
 *
 * <p>[회귀] 기본 정책이 {@code anyRequest().authenticated()} 라, 어떤 규칙에도 걸리지 않는
 * 오타·죽은 링크까지 로그인 화면(302)으로 넘어갔다. 방문자에게는 없는 페이지를 눌렀는데
 * 로그인을 요구하는 것처럼 보였고, 크롤러는 404 대신 200 을 받아 없는 URL 을 계속 살아 있는
 * 페이지로 취급했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnmappedUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("[회귀] 존재하지 않는 주소는 로그인 화면이 아니라 404 다")
    void unmappedUrlReturnsNotFound() throws Exception {
        mockMvc.perform(get("/this-path-does-not-exist"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/오타난-주소"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("보호된 주소는 그대로 로그인 화면으로 보낸다")
    void protectedUrlStillRedirectsToLogin() throws Exception {
        // 존재 여부를 404/302 로 흘리지 않기 위해, 매핑이 있는 주소는 기존대로 처리한다.
        mockMvc.perform(get("/mypage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/loginView"));
    }
}
