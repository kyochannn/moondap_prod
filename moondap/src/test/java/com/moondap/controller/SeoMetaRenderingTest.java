package com.moondap.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

/**
 * 레이아웃이 실제로 SEO 메타 태그를 출력하는지 확인한다.
 *
 * <p>인터셉터가 값을 잘 만들어도 템플릿이 그것을 쓰지 않으면 의미가 없다. 표현식 오타는
 * 컴파일에서 걸리지 않고 렌더링 시점에야 드러나므로 렌더된 HTML 로 검증한다.
 *
 * <p>DB 에 접속하지 않는 /privacy, /terms 만 대상으로 한다.
 * (테스트 프로파일은 실제 DataSource 연결이 없다. application-test.properties 참고)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeoMetaRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("페이지마다 고유한 description 과 canonical 이 출력된다")
    void rendersPageSpecificMeta() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<link rel=\"canonical\" href=\"https://moondap.com/privacy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<meta property=\"og:url\" content=\"https://moondap.com/privacy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("개인정보처리방침 - 문답")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<meta name=\"robots\" content=\"index, follow\"")));

        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<link rel=\"canonical\" href=\"https://moondap.com/terms\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이용약관 - 문답")));
    }

    @Test
    @DisplayName("[회귀] 모든 페이지가 한국어임을 선언한다")
    void declaresLanguage() throws Exception {
        // lang 이 없으면 검색엔진의 언어 판별과 스크린리더 발음이 모두 어긋난다.
        // 콘텐츠 템플릿의 <html> 태그는 레이아웃 방언이 버리므로 main_layout 에만 둔다.
        mockMvc.perform(get("/privacy"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"ko\"")));
        mockMvc.perform(get("/terms"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<html lang=\"ko\"")));
    }

    @Test
    @DisplayName("[회귀] og:image 는 절대 URL 이어야 SNS 크롤러가 읽는다")
    void rendersAbsoluteOgImage() throws Exception {
        // 이전에는 content="/assets/img/logo.webp" 라 공유 카드 이미지가 비어 있었다.
        mockMvc.perform(get("/privacy"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<meta property=\"og:image\" content=\"https://moondap.com/assets/img/logo.webp\"")));
    }

    @Test
    @DisplayName("[회귀] 전 페이지가 같은 description 을 쓰지 않는다")
    void descriptionsDiffer() throws Exception {
        String privacy = descriptionOf(mockMvc.perform(get("/privacy"))
                .andReturn().getResponse().getContentAsString());
        String terms = descriptionOf(mockMvc.perform(get("/terms"))
                .andReturn().getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(privacy).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(terms).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(privacy).isNotEqualTo(terms);
    }

    /** 렌더된 HTML 에서 meta description 값 하나만 뽑는다. 중복 출력이면 첫 번째가 잡힌다. */
    private String descriptionOf(String html) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<meta name=\"description\" content=\"([^\"]*)\"")
                .matcher(html);
        return matcher.find() && StringUtils.hasText(matcher.group(1)) ? matcher.group(1) : "";
    }

    @Test
    @DisplayName("robots.txt 와 ads.txt 가 로그인 없이 열린다")
    void servesCrawlerFiles() throws Exception {
        // 애드센스는 ads.txt 를 익명으로 가져간다. 시큐리티 기본값이 authenticated 라
        // permitAll 을 빠뜨리면 심사에서 '판매자 정보 없음'으로 표시된다.
        mockMvc.perform(get("/ads.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pub-5509463812555494")));

        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Sitemap: https://moondap.com/sitemap.xml")));
    }

    @Test
    @DisplayName("sitemap.xml 이 로그인 없이 열리고, DB 가 죽어도 200 을 준다")
    void servesSitemapWithoutDatabase() throws Exception {
        // 테스트 프로파일에는 접속 가능한 DB 가 없다. 콘텐츠 목록 조회는 실패하지만
        // sitemap 이 500 을 내면 구글 서치콘솔에 오류로 기록되므로 정적 URL 이라도 내려줘야 한다.
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<loc>https://moondap.com/test/list</loc>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("</urlset>")));
    }
}
