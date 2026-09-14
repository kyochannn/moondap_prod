package com.moondap.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.moondap.service.StatService;

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
        verify(statService).recordVisit(anyString(), eq("/test/love-style"), any());
    }

    @Test
    @DisplayName("봇은 세지 않는다")
    void skipsBots() {
        handle(request("GET", "/", "Mozilla/5.0 (compatible; Googlebot/2.1)", null));
        handle(request("GET", "/", "Mozilla/5.0 (compatible; bingbot/2.0)", null));
        handle(request("GET", "/", "python-requests/2.31.0", null));

        // 사이트맵을 보고 들어오는 크롤러가 사람 수를 덮어버리면 지표가 무의미해진다.
        verify(statService, never()).recordVisit(anyString(), anyString(), any());
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

        verify(statService, org.mockito.Mockito.times(3)).recordVisit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("다음 크롤러는 여전히 거른다")
    void stillSkipsDaumCrawler() {
        handle(request("GET", "/", "Mozilla/5.0 (compatible; Daum/4.1; +http://cs.daum.net/)", null));
        handle(request("GET", "/", "Daumoa/3.0", null));

        verify(statService, never()).recordVisit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("User-Agent 가 없으면 세지 않는다")
    void skipsMissingUserAgent() {
        handle(request("GET", "/", null, null));
        handle(request("GET", "/", "", null));

        verify(statService, never()).recordVisit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("AJAX 요청은 세지 않는다")
    void skipsAjax() {
        // 한 화면에서 여러 번 나가므로 방문으로 치면 같은 사람이 여러 번 잡힌다.
        handle(request("GET", "/balanceGame/selectBalanceGameList", BROWSER, "XMLHttpRequest"));

        verify(statService, never()).recordVisit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("GET 이 아닌 요청은 세지 않는다")
    void skipsNonGet() {
        handle(request("POST", "/balanceGame/vote", BROWSER, null));

        verify(statService, never()).recordVisit(anyString(), anyString(), any());
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

        verify(statService, never()).recordVisit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("브라우저가 알아서 가져가는 파일은 세지 않는다")
    void skipsBrowserFetchedFiles() {
        // manifest.json, favicon 등은 사람이 연 화면이 아니다.
        handle(request("GET", "/manifest.json", BROWSER, null));
        handle(request("GET", "/favicon.png", BROWSER, null));
        handle(request("GET", "/sitemap.xml", BROWSER, null));

        verify(statService, never()).recordVisit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("관리자 화면은 세지 않는다")
    void skipsAdmin() {
        // 운영자가 통계를 보러 들어온 것이 그 통계에 섞이면 안 된다.
        handle(request("GET", "/admin/stats", BROWSER, null));

        verify(statService, never()).recordVisit(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("집계가 실패해도 화면 요청은 계속 진행된다")
    void survivesFailure() {
        org.mockito.Mockito.doThrow(new RuntimeException("DB 장애"))
                .when(statService).recordVisit(anyString(), anyString(), any());

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
        verify(statService).recordVisit(anyString(), eq("/test/love-style"), any());

        handle(request("GET", "/test/love-style;jsessionid=ABC123", BROWSER, null));
        verify(statService, org.mockito.Mockito.times(2))
                .recordVisit(anyString(), eq("/test/love-style"), any());
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

        verify(statService).recordVisit(anyString(), eq("/test/love-style"), isNull());
    }

    @Test
    @DisplayName("검색으로 들어오면 출처가 남는다")
    void recordsSearchEntry() {
        MockHttpServletRequest request = request("GET", "/test/love-style", BROWSER, null);
        request.setServerName("moondap.com");
        request.addHeader("Referer", "https://m.search.naver.com/search.naver?query=%EC%97%B0%EC%95%A0");

        handle(request);

        verify(statService).recordVisit(anyString(), eq("/test/love-style"), eq("네이버"));
    }

    @Test
    @DisplayName("Referer 가 없으면 직접 유입으로 남는다")
    void recordsDirectEntry() {
        handle(request("GET", "/", BROWSER, null));

        verify(statService).recordVisit(anyString(), eq("/"), eq(com.moondap.common.TrafficSource.DIRECT));
    }

    private boolean handle(MockHttpServletRequest request) {
        return new VisitLogInterceptor(statService)
                .preHandle(request, new MockHttpServletResponse(), new Object());
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
