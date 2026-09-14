package com.moondap.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moondap.dto.MdContentItemDTO;
import com.moondap.service.BalanceGameService;
import com.moondap.service.EgenTetoService;
import com.moondap.service.MdTestAdminService;
import com.moondap.service.MdTestUserService;

/**
 * 소개·문의 페이지.
 *
 * <p>애드센스 심사는 개인정보처리방침·이용약관과 함께 운영 주체를 밝히는 소개 페이지와
 * 연락 경로를 관행적으로 확인한다. 두 페이지 모두 링크가 끊기면 있으나 마나이므로
 * 라우팅과 푸터 연결까지 함께 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AboutContactPageTest {

    @Autowired
    private MockMvc mockMvc;

    // 소개 페이지는 참여자 수·콘텐츠 수를 집계한다. 테스트 환경에는 DB 가 없어 대역으로 둔다.
    @MockitoBean
    private BalanceGameService balanceGameService;
    @MockitoBean
    private MdTestAdminService mdTestAdminService;
    @MockitoBean
    private EgenTetoService egenTetoService;
    @MockitoBean
    private MdTestUserService mdTestUserService;

    @BeforeEach
    void stubStats() {
        when(balanceGameService.getTotalParticipantCount()).thenReturn(1200L);
        when(mdTestAdminService.getTotalPlayCount()).thenReturn(3400L);
        when(egenTetoService.getScoreStatistics()).thenReturn(Map.of("totalCount", 400L));
        when(mdTestUserService.getAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(List.of(new MdContentItemDTO(), new MdContentItemDTO()));
    }

    @Test
    @DisplayName("소개 페이지가 열리고 집계 수치가 렌더링된다")
    void rendersAboutPage() throws Exception {
        String html = mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("문답 소개");
        assertThat(html).contains("5,000");   // 1200 + 3400 + 400, 천 단위 구분
        assertThat(html).contains("운영 원칙");
        // 결과가 진단이 아님을 밝히는 문구는 심리테스트 사이트에서 특히 중요하다.
        assertThat(html).contains("진단이 아닙니다");
    }

    @Test
    @DisplayName("문의 페이지가 열리고 연락 수단이 노출된다")
    void rendersContactPage() throws Exception {
        String html = mockMvc.perform(get("/contact"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("webmaster@moondap.com");
        assertThat(html).contains("mailto:webmaster@moondap.com");
        assertThat(html).contains("콘텐츠 신고");
    }

    @Test
    @DisplayName("두 페이지 모두 고유 canonical 과 description 을 가진다")
    void hasOwnSeoMeta() throws Exception {
        String about = mockMvc.perform(get("/about")).andReturn().getResponse().getContentAsString();
        String contact = mockMvc.perform(get("/contact")).andReturn().getResponse().getContentAsString();

        assertThat(about).contains("<link rel=\"canonical\" href=\"https://moondap.com/about\"");
        assertThat(contact).contains("<link rel=\"canonical\" href=\"https://moondap.com/contact\"");
        assertThat(descriptionOf(about)).isNotBlank().isNotEqualTo(descriptionOf(contact));
    }

    @Test
    @DisplayName("[회귀] 푸터에서 소개·문의 페이지로 갈 수 있다")
    void footerLinksToBothPages() throws Exception {
        // 페이지만 만들고 링크를 걸지 않으면 심사자도 이용자도 찾지 못한다.
        String html = mockMvc.perform(get("/contact")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("href=\"/about\"");
        assertThat(html).contains("href=\"/contact\"");
    }

    @Test
    @DisplayName("로그인 없이 열린다")
    void accessibleAnonymously() throws Exception {
        // 시큐리티 기본값이 authenticated 라 permitAll 을 빠뜨리면 로그인으로 튕긴다.
        mockMvc.perform(get("/about")).andExpect(status().isOk());
        mockMvc.perform(get("/contact")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("sitemap 에 두 페이지가 들어간다")
    void listedInSitemap() throws Exception {
        String xml = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(xml).contains("<loc>https://moondap.com/about</loc>");
        assertThat(xml).contains("<loc>https://moondap.com/contact</loc>");
    }

    private String descriptionOf(String html) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<meta name=\"description\" content=\"([^\"]*)\"")
                .matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }
}
