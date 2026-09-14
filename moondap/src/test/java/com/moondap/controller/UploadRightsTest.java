package com.moondap.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdUserDTO;
import com.moondap.service.MdTestAdminService;
import com.moondap.service.MdTestCategoryService;

/**
 * 업로드 이미지 권리 확인의 서버 측 강제.
 *
 * <p>타인의 저작물이 올라오면 광고가 붙은 페이지에 저작권 침해물이 노출된다.
 * 화면의 체크박스는 폼을 직접 조작하면 우회되므로 실제 차단은 컨트롤러가 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadRightsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MdTestAdminService mdTestAdminService;

    /** 등록 화면이 카테고리 목록을 읽는다. 테스트 환경에는 DB 가 없다. */
    @MockitoBean
    private MdTestCategoryService mdTestCategoryService;

    /**
     * 컨트롤러가 @AuthenticationPrincipal PrincipalDetails 를 받고, 헤더 프래그먼트가
     * #authentication.principal.user 를 읽는다. @WithMockUser 의 기본 principal 로는
     * 둘 다 깨지므로 실제 타입을 만들어 넣는다.
     */
    private PrincipalDetails principal() {
        MdUserDTO user = new MdUserDTO();
        user.setUsername("tester");
        user.setPassword("pw");
        user.setNickname("테스터");
        user.setRole("ROLE_USER");
        return new PrincipalDetails(user);
    }

    private MockMultipartFile thumbnail() {
        return new MockMultipartFile("thumbnailFile", "thumb.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("[회귀] 권리 확인 없이는 테스트를 등록할 수 없다")
    void rejectsTestInsertWithoutCopyrightAgreement() throws Exception {
        // 체크박스는 해제하면 파라미터 자체가 전송되지 않는다.
        mockMvc.perform(multipart("/test/manage/insert").file(thumbnail()).with(csrf()).with(user(principal()))
                        .param("title", "테스트")
                        .param("testKey", "sample"))
                .andExpect(status().isBadRequest());

        verify(mdTestAdminService, never()).createTest(any(), any(), any(), any());
    }

    @Test
    @DisplayName("[회귀] false 로 조작해 보내도 막는다")
    void rejectsForgedFalse() throws Exception {
        mockMvc.perform(multipart("/test/manage/insert").file(thumbnail()).with(csrf()).with(user(principal()))
                        .param("title", "테스트")
                        .param("agreeCopyright", "false"))
                .andExpect(status().isBadRequest());

        verify(mdTestAdminService, never()).createTest(any(), any(), any(), any());
    }

    @Test
    @DisplayName("권리를 확인하면 등록이 진행된다")
    void allowsInsertWhenAgreed() throws Exception {
        mockMvc.perform(multipart("/test/manage/insert").file(thumbnail()).with(csrf()).with(user(principal()))
                        .param("title", "테스트")
                        .param("agreeCopyright", "true"))
                .andExpect(status().isOk());

        verify(mdTestAdminService).createTest(any(), any(), any(), any());
    }

    @Test
    @DisplayName("등록 화면에 권리 확인 안내와 체크박스가 보인다")
    void rendersNoticeOnInsertForm() throws Exception {
        String html = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/test/manage/insertTestView").with(user(principal()))
                                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"agreeCopyright\"");
        assertThat(html).contains("업로드 이미지 권리 확인");
        // 미리 체크돼 있으면 확인의 의미가 없다.
        assertThat(html).doesNotContain("id=\"agreeCopyright\"\n                   name=\"agreeCopyright\" value=\"true\" checked");
        // 약관 근거 조항으로 이어져야 한다.
        assertThat(html).contains("이용약관 제5조");
    }
}
