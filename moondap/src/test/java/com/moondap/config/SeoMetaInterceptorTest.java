package com.moondap.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import com.moondap.dto.SeoMetaDTO;

/**
 * canonical / robots 메타 생성 규칙.
 *
 * <p>애드센스가 '가치 없는 콘텐츠'로 판정한 핵심 원인은 두 가지였다.
 * 결과 페이지가 쿼리스트링 조합마다 별개의 얇은 페이지로 색인된 것, 그리고
 * 읽을거리 없는 중간 화면이 색인 대부분을 차지한 것이다. 둘 다 여기서 막는다.
 */
class SeoMetaInterceptorTest {

    private final SeoMetaInterceptor interceptor = new SeoMetaInterceptor("https://moondap.com/");

    private SeoMetaDTO run(String uri, String queryString, SeoMetaDTO preset) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (queryString != null) {
            request.setQueryString(queryString);
        }
        ModelAndView mv = new ModelAndView("some/view");
        if (preset != null) {
            mv.addObject(SeoMetaInterceptor.MODEL_ATTRIBUTE, preset);
        }
        interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), mv);
        return (SeoMetaDTO) mv.getModel().get(SeoMetaInterceptor.MODEL_ATTRIBUTE);
    }

    @Test
    @DisplayName("canonical 은 쿼리스트링을 버린다")
    void canonicalDropsQueryString() {
        // ?score=&answers= 조합마다 색인되던 것이 중복 콘텐츠의 원인이었다.
        SeoMetaDTO seo = run("/test/love-type/result", "resultCode=A&score=17&answers=%5B1%2C2%5D", null);
        assertThat(seo.getCanonical()).isEqualTo("https://moondap.com/test/love-type/result");
    }

    @Test
    @DisplayName("baseUrl 끝의 슬래시가 이중으로 붙지 않는다")
    void doesNotDoubleSlash() {
        assertThat(run("/privacy", null, null).getCanonical()).isEqualTo("https://moondap.com/privacy");
    }

    @Test
    @DisplayName("컨트롤러가 지정한 canonical 이 우선하고, 상대경로는 절대 URL 로 바뀐다")
    void controllerCanonicalWins() {
        SeoMetaDTO preset = new SeoMetaDTO();
        preset.setCanonical("/balanceGame/selectBalanceGameView?id=BG000012");

        SeoMetaDTO seo = run("/balanceGame/selectBalanceGameView", "id=BG000012&category=love", preset);

        // id 는 콘텐츠를 결정하므로 남기고, 탐색용 category 는 뗀다.
        assertThat(seo.getCanonical())
                .isEqualTo("https://moondap.com/balanceGame/selectBalanceGameView?id=BG000012");
    }

    @Test
    @DisplayName("읽을거리가 없는 화면은 noindex 로 표시한다")
    void marksThinPagesNoindex() {
        assertThat(run("/test/love-type/questions", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/test/love-type/result", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/egenTeto/result", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/loginView", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/mypage", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/admin/test/list", null, null).getRobots()).isEqualTo("noindex, follow");
    }

    @Test
    @DisplayName("콘텐츠 페이지는 색인을 허용한다")
    void allowsIndexingOfContentPages() {
        assertThat(run("/", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/test/list", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/test/love-type", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/balanceGame/selectBalanceGameView", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/egenTeto/selectEgenTetoGame", null, null).getRobots()).isEqualTo("index, follow");
    }

    @Test
    @DisplayName("여러 줄 소개글을 meta 속성에 넣을 수 있게 정리한다")
    void squashesMultilineDescription() {
        SeoMetaDTO preset = new SeoMetaDTO();
        preset.setDescription("첫 줄\n\n  둘째 줄\t셋째");

        assertThat(run("/test/x", null, preset).getDescription()).isEqualTo("첫 줄 둘째 줄 셋째");
    }

    @Test
    @DisplayName("긴 설명은 검색 결과에 보이는 길이로 자른다")
    void truncatesLongDescription() {
        SeoMetaDTO preset = new SeoMetaDTO();
        preset.setDescription("가".repeat(300));

        String description = run("/test/x", null, preset).getDescription();

        assertThat(description).hasSize(160).endsWith("…");
    }

    @Test
    @DisplayName("값을 지정하지 않은 페이지는 사이트 기본값을 쓴다")
    void fillsDefaults() {
        SeoMetaDTO seo = run("/", null, null);

        assertThat(seo.getTitle()).isNotBlank();
        assertThat(seo.getDescription()).isNotBlank();
        assertThat(seo.getImage()).isEqualTo("https://moondap.com/assets/img/logo.webp");
        assertThat(seo.getOgType()).isEqualTo("website");
    }

    @Test
    @DisplayName("업로드 썸네일 상대경로는 절대 URL 로 만든다")
    void absolutizesUploadedImage() {
        SeoMetaDTO preset = new SeoMetaDTO();
        preset.setImage("/uploads/thumb.png");

        // og:image 는 절대 URL 이어야 SNS 크롤러가 읽는다.
        assertThat(run("/test/x", null, preset).getImage())
                .isEqualTo("https://moondap.com/uploads/thumb.png");
    }

    @Test
    @DisplayName("AJAX 프래그먼트와 리다이렉트 응답에는 손대지 않는다")
    void skipsNonPageResponses() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/list");

        ModelAndView fragment = new ModelAndView("test/list :: #content-grid");
        interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), fragment);
        assertThat(fragment.getModel()).doesNotContainKey(SeoMetaInterceptor.MODEL_ATTRIBUTE);

        ModelAndView redirect = new ModelAndView("redirect:/");
        interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), redirect);
        assertThat(redirect.getModel()).doesNotContainKey(SeoMetaInterceptor.MODEL_ATTRIBUTE);
    }
}
