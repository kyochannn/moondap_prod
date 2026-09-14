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
 *
 * <p>심리테스트 결과 페이지는 한때 noindex 였다가 색인 대상으로 바뀌었다. 결과 본문이
 * 유형당 1,000자 안팎으로 사이트에서 가장 긴 글인데 정작 색인되는 것은 220자짜리
 * 소개글뿐이어서, 콘텐츠가 있는데도 얇은 사이트로 보였다. 중복 URL 은 noindex 가 아니라
 * 컨트롤러가 지정하는 canonical 로 막는다 — 아래 두 테스트가 그 역할 분담을 고정한다.
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
        // 에겐테토 결과만 예외로 남는다. URL 키가 결과 유형이 아니라 응시 건마다 발급되는
        // UUID 라, 같은 유형이 응시 횟수만큼 다른 URL 로 복제된다. 묶어 줄 대표 URL 이 없다.
        assertThat(run("/egenTeto/result", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/loginView", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/mypage", null, null).getRobots()).isEqualTo("noindex, follow");
        assertThat(run("/admin/test/list", null, null).getRobots()).isEqualTo("noindex, follow");
        // 보관함은 로그인 없이 열리는(익명 쿠키 기반) 개인화 화면이라 방문자마다 내용이
        // 다르다. robots.txt 차단만으로는 이미 색인된 URL 을 뺄 수 없어 noindex 도 둔다.
        assertThat(run("/my/results", null, null).getRobots()).isEqualTo("noindex, follow");
    }

    @Test
    @DisplayName("콘텐츠 페이지는 색인을 허용한다")
    void allowsIndexingOfContentPages() {
        assertThat(run("/", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/test/list", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/test/love-type", null, null).getRobots()).isEqualTo("index, follow");
        // 결과 본문은 사이트에서 가장 긴 글이다. 이걸 빼면 색인에 남는 것은 소개글뿐이다.
        assertThat(run("/test/love-type/result", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/balanceGame/selectBalanceGameView", null, null).getRobots()).isEqualTo("index, follow");
        assertThat(run("/egenTeto/selectEgenTetoGame", null, null).getRobots()).isEqualTo("index, follow");
    }

    @Test
    @DisplayName("결과 페이지의 canonical 은 resultCode 만 남기고 score 는 버린다")
    void resultCanonicalKeepsOnlyResultCode() {
        // 결과 페이지에 닿는 경로가 셋이다. 응시 직후의 POST(쿼리 없음), 공유 링크
        // (?resultCode=), 점수까지 붙은 공유 링크(&score=). score 는 사람마다 다르지만
        // 본문을 바꾸지 않으므로, 셋 다 하나의 URL 로 모여야 결과 하나당 페이지도 하나가 된다.
        SeoMetaDTO preset = new SeoMetaDTO();
        preset.setCanonical("/test/love-type/result?resultCode=3");

        SeoMetaDTO seo = run("/test/love-type/result", "resultCode=3&score=41", preset);

        assertThat(seo.getCanonical()).isEqualTo("https://moondap.com/test/love-type/result?resultCode=3");
        assertThat(seo.getRobots()).isEqualTo("index, follow");
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
