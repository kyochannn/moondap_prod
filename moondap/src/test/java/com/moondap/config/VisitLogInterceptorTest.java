package com.moondap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.moondap.common.AnonymousIdentity;
import com.moondap.common.TrafficSource;
import com.moondap.dto.VisitContext;
import com.moondap.service.StatService;

import jakarta.servlet.http.Cookie;

/**
 * 방문 집계 대상 판정.
 *
 * <p>예전에는 메인 컨트롤러 안에서만 집계해서, 검색으로 테스트 페이지에 바로 들어온
 * 방문자가 한 명도 잡히지 않았다. 전 페이지로 넓히면서 반대로 "사람이 아닌 접속"까지
 * 세게 될 위험이 생겼다. 어느 쪽이 세어지고 어느 쪽이 빠지는지를 여기서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitLogInterceptorTest {

    private static final String BROWSER =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";

    @Mock
    private StatService statService;

    @Test
    @DisplayName("[회귀] 메인이 아닌 페이지 방문도 집계된다")
    void countsNonMainPages() {
        handle(request("GET", "/test/love-style", BROWSER, null));

        // 이 한 줄이 빠져 있어서 검색 유입이 통째로 누락됐다.
        assertThat(captureVisit().path()).isEqualTo("/test/love-style");
    }

    @Test
    @DisplayName("봇은 세지 않는다")
    void skipsBots() {
        handle(request("GET", "/", "Mozilla/5.0 (compatible; Googlebot/2.1)", null));
        handle(request("GET", "/", "Mozilla/5.0 (compatible; bingbot/2.0)", null));
        handle(request("GET", "/", "python-requests/2.31.0", null));

        // 사이트맵을 보고 들어오는 크롤러가 사람 수를 덮어버리면 지표가 무의미해진다.
        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("[회귀] 국내 인앱 브라우저는 사람으로 센다")
    void countsKoreanInAppBrowsers() {
        // "daum" 으로 거르면 다음 앱(DaumApps/6.9.x)까지 봇이 되어 실제 방문자가 빠진다.
        handle(request("GET", "/", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                + "AppleWebKit/605.1.15 DaumApps/6.9.41 DaumDevice/mobile", null));
        handle(request("GET", "/", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                + "KAKAOTALK/10.4.0", null));
        handle(request("GET", "/", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                + "NAVER(inapp; search; 2000; 12.9.0)", null));

        verify(statService, org.mockito.Mockito.times(3)).recordVisit(any());
    }

    @Test
    @DisplayName("다음 크롤러는 여전히 거른다")
    void stillSkipsDaumCrawler() {
        handle(request("GET", "/", "Mozilla/5.0 (compatible; Daum/4.1; +http://cs.daum.net/)", null));
        handle(request("GET", "/", "Daumoa/3.0", null));

        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("User-Agent 가 없으면 세지 않는다")
    void skipsMissingUserAgent() {
        handle(request("GET", "/", null, null));
        handle(request("GET", "/", "", null));

        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("AJAX 요청은 세지 않는다")
    void skipsAjax() {
        // 한 화면에서 여러 번 나가므로 방문으로 치면 같은 사람이 여러 번 잡힌다.
        handle(request("GET", "/balanceGame/selectBalanceGameList", BROWSER, "XMLHttpRequest"));

        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("GET 이 아닌 요청은 세지 않는다")
    void skipsNonGet() {
        handle(request("POST", "/balanceGame/vote", BROWSER, null));

        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("[회귀] 정적 리소스는 세지 않는다")
    void skipsStaticResources() {
        // 인터셉터는 정적 파일 요청에도 걸린다. 화면 하나를 열 때마다 js·css·이미지
        // 수십 건이 함께 집계돼서, 시간대별 접속 건수가 실제의 수십 배로 부풀었다.
        var resourceHandler = new org.springframework.web.servlet.resource.ResourceHttpRequestHandler();

        new VisitLogInterceptor(statService).preHandle(
                request("GET", "/assets/js/main.js", BROWSER, null),
                new MockHttpServletResponse(), resourceHandler);

        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("브라우저가 알아서 가져가는 파일은 세지 않는다")
    void skipsBrowserFetchedFiles() {
        // manifest.json, favicon 등은 사람이 연 화면이 아니다.
        handle(request("GET", "/manifest.json", BROWSER, null));
        handle(request("GET", "/favicon.png", BROWSER, null));
        handle(request("GET", "/sitemap.xml", BROWSER, null));

        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("관리자 화면은 세지 않는다")
    void skipsAdmin() {
        // 운영자가 통계를 보러 들어온 것이 그 통계에 섞이면 안 된다.
        handle(request("GET", "/admin/stats", BROWSER, null));

        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("집계가 실패해도 화면 요청은 계속 진행된다")
    void survivesFailure() {
        org.mockito.Mockito.doThrow(new RuntimeException("DB 장애"))
                .when(statService).recordVisit(any());

        // 예외가 밖으로 나가면 통계 한 건 때문에 페이지 전체가 오류가 된다.
        boolean proceeded = handle(request("GET", "/", BROWSER, null));

        org.assertj.core.api.Assertions.assertThat(proceeded).isTrue();
    }

    @Test
    @DisplayName("같은 화면이 여러 경로로 갈리지 않는다")
    void normalizesPath() {
        // 뒤 슬래시와 ;jsessionid 가 붙으면 같은 화면이 별개의 행으로 쌓여,
        // 실제로 가장 많이 열린 화면이 상위 목록에서 사라진다.
        handle(request("GET", "/test/love-style/", BROWSER, null));
        handle(request("GET", "/test/love-style;jsessionid=ABC123", BROWSER, null));

        assertThat(captureVisits()).extracting(VisitContext::path)
                .containsExactly("/test/love-style", "/test/love-style");
    }

    @Test
    @DisplayName("사이트 안에서의 이동은 유입으로 세지 않는다")
    void internalNavigationIsNotEntry() {
        // 메인에서 테스트로 넘어간 클릭까지 유입으로 세면 '직접 유입'이 실제의 몇 배로
        // 부풀어, 검색·SNS 가 실제로 데려오는 사람 수가 묻힌다.
        MockHttpServletRequest request = request("GET", "/test/love-style", BROWSER, null);
        request.setServerName("moondap.com");
        request.addHeader("Referer", "https://www.moondap.com/");

        handle(request);

        assertThat(captureVisit().trafficSource()).isNull();
    }

    @Test
    @DisplayName("검색으로 들어오면 출처가 남는다")
    void recordsSearchEntry() {
        MockHttpServletRequest request = request("GET", "/test/love-style", BROWSER, null);
        request.setServerName("moondap.com");
        request.addHeader("Referer", "https://m.search.naver.com/search.naver?query=%EC%97%B0%EC%95%A0");

        handle(request);

        assertThat(captureVisit().trafficSource()).isEqualTo("네이버");
    }

    @Test
    @DisplayName("Referer 가 없으면 직접 유입으로 남는다")
    void recordsDirectEntry() {
        handle(request("GET", "/", BROWSER, null));

        assertThat(captureVisit().trafficSource()).isEqualTo(TrafficSource.DIRECT);
    }

    // ── 익명 쿠키 기준 방문자 ────────────────────────────────

    @Test
    @DisplayName("[회귀] 방금 발급한 쿠키는 방문자로 세지 않는다")
    void doesNotCountFreshlyIssuedCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 쿠키를 저장하지 않는 클라이언트는 요청마다 새 UUID 를 받는다.
        // 발급 시점에 세면 그 한 대가 하루 수백 명으로 둔갑한다.
        handle(request("GET", "/", BROWSER, null), response);

        assertThat(captureVisit().anonId()).isNull();
        // 그래도 쿠키는 내려보낸다 — 다음 방문부터 같은 사람인지 알아야 한다.
        assertThat(response.getHeader("Set-Cookie")).contains(AnonymousIdentity.COOKIE_NAME);
    }

    @Test
    @DisplayName("되돌아온 쿠키는 방문자로 센다")
    void countsReturningCookie() {
        MockHttpServletRequest request = request("GET", "/", BROWSER, null);
        request.setCookies(new Cookie(AnonymousIdentity.COOKIE_NAME, "anon-123"));

        handle(request);

        assertThat(captureVisit().anonId()).isEqualTo("anon-123");
    }

    // ── User-Agent 계측 ─────────────────────────────────────

    @Test
    @DisplayName("봇으로 걸러낸 요청도 User-Agent 는 남긴다")
    void recordsFilteredUserAgent() {
        // 남기지 않으면 "멀쩡한 브라우저를 봇으로 거르고 있지 않은가"를 확인할 길이 없다.
        // 실제로 "daum" 조각 하나 때문에 다음 앱 방문자가 통째로 누락된 적이 있다.
        handle(request("GET", "/", "Mozilla/5.0 (compatible; Googlebot/2.1)", null));

        verify(statService).recordUserAgent(anyString(), eq(false));
        verify(statService, never()).recordVisit(any());
    }

    @Test
    @DisplayName("사람으로 센 요청은 counted 로 남는다")
    void recordsCountedUserAgent() {
        handle(request("GET", "/", BROWSER, null));

        verify(statService).recordUserAgent(eq(BROWSER), eq(true));
    }

    @Test
    @DisplayName("정적 리소스는 User-Agent 계측에도 넣지 않는다")
    void doesNotMeasureStaticResources() {
        // 화면 하나에 딸려 오는 js·css 까지 세면 목록이 그 브라우저 하나로 덮인다.
        new VisitLogInterceptor(statService).preHandle(
                request("GET", "/assets/js/main.js", BROWSER, null),
                new MockHttpServletResponse(),
                new org.springframework.web.servlet.resource.ResourceHttpRequestHandler());

        verify(statService, never()).recordUserAgent(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /** 집계에 넘어간 방문 정보. 여러 번 호출됐다면 첫 번째. */
    private VisitContext captureVisit() {
        return captureVisits().get(0);
    }

    private java.util.List<VisitContext> captureVisits() {
        ArgumentCaptor<VisitContext> captor = ArgumentCaptor.forClass(VisitContext.class);
        verify(statService, org.mockito.Mockito.atLeastOnce()).recordVisit(captor.capture());
        return captor.getAllValues();
    }

    private boolean handle(MockHttpServletRequest request) {
        return handle(request, new MockHttpServletResponse());
    }

    private boolean handle(MockHttpServletRequest request, MockHttpServletResponse response) {
        // 운영에서는 RequestContextFilter 가 해 주는 일이다.
        // AnonymousIdentity 가 RequestContextHolder 에서 요청을 꺼내 쿠키를 읽는다.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            return new VisitLogInterceptor(statService).preHandle(request, response, new Object());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private MockHttpServletRequest request(String method, String uri, String userAgent, String requestedWith) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("203.0.113.7");
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        if (requestedWith != null) {
            request.addHeader("X-Requested-With", requestedWith);
        }
        return request;
    }
}
