package com.moondap.config;

import com.moondap.common.CommonUtil;
import com.moondap.common.TrafficSource;
import com.moondap.service.StatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * 방문 집계.
 *
 * <p>이전에는 {@code MainController.index()} 안에서만 집계했다. 그래서 검색이나 SNS 로
 * {@code /test/xxx} 에 바로 들어온 사람은 한 명도 잡히지 않았다. 사이트맵을 동적으로
 * 만들기 시작한 뒤로는 그 유입이 계속 늘고 있어서, 관리자 화면의 방문자 수가 실제보다
 * 낮게 나오는 상태였다.
 *
 * <p>집계는 화면 요청에만 한다. 아래는 일부러 제외한다.
 * <ul>
 *   <li><b>GET 이 아닌 요청</b> — 투표·댓글 같은 동작은 참여 수가 따로 센다</li>
 *   <li><b>AJAX</b> — 한 화면에서 여러 번 나가므로 방문으로 치면 중복이다</li>
 *   <li><b>봇</b> — 사이트맵을 보고 들어오는 크롤러가 사람 수를 덮어버린다</li>
 *   <li><b>관리자 화면</b> — 운영자 본인의 접속이 지표에 섞이면 안 된다</li>
 * </ul>
 *
 * <p>집계 실패가 화면을 막지 않도록 예외를 삼킨다. 통계 한 건보다 페이지가 열리는 것이
 * 중요하다.
 *
 * <p><b>알려진 한계</b> — 집계는 {@code preHandle} 에서 하므로 응답 상태를 모른다.
 * 매핑이 아예 없는 주소(스캐너가 찍어 보는 {@code /wp-admin/...} 같은 것)는 시큐리티
 * 필터에서 404 로 끝나 여기까지 오지 않으므로 문제가 없지만, <b>매핑은 있고 콘텐츠만
 * 없는 경로</b>({@code /test/없는키} → {@link com.moondap.common.exception.ContentNotFoundException})
 * 는 조회 1건으로 남는다. 상태를 보려면 집계를 {@code afterCompletion} 으로 옮겨야 하는데
 * 그러면 방문자 수의 의미까지 같이 바뀌어 지난 기록과 비교할 수 없게 된다.
 * 지표의 연속성을 택했다.
 */
@Slf4j
@RequiredArgsConstructor
public class VisitLogInterceptor implements HandlerInterceptor {

    private final StatService statService;

    /**
     * 봇 판별용 User-Agent 조각.
     *
     * <p>완벽할 수 없다. 목적은 "구글·네이버 크롤러가 하루 수백 번 들어와 방문자 수를
     * 부풀리는 것"을 막는 정도다. 놓친 봇이 섞이는 것이 사람을 빠뜨리는 것보다 낫다.
     */
    private static final String[] BOT_MARKERS = {
            "bot", "crawl", "spider", "slurp", "scrapy", "curl", "wget",
            "python-requests", "okhttp", "headlesschrome", "facebookexternalhit",
            "embedly", "quora link preview", "bitlybot", "yeti",
            // 다음 크롤러. "daum" 만으로 거르면 다음 앱 인앱 브라우저(DaumApps/6.9.x)까지
            // 봇으로 걸러져 실제 방문자가 통째로 누락된다.
            "daumoa", "daum/"
    };

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        if (shouldCount(request, handler)) {
            try {
                statService.recordVisit(
                        CommonUtil.getClientIp(request),
                        normalizePath(request.getRequestURI()),
                        TrafficSource.classify(request.getHeader("Referer"), request.getServerName()));
            } catch (Exception e) {
                log.error("방문 집계 실패: uri={}", request.getRequestURI(), e);
            }
        }
        return true;
    }

    /** md_pageview_path.path 컬럼 길이. */
    private static final int MAX_PATH_LENGTH = 191;

    /**
     * 같은 화면이 여러 행으로 갈리지 않도록 경로를 다듬는다.
     *
     * <p>{@code /test/abc} 와 {@code /test/abc/} 가 따로 쌓이면 상위 목록에서 둘 다
     * 순위 밖으로 밀려, 실제로 가장 많이 열린 화면이 보이지 않는다.
     */
    private String normalizePath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }

        String path = uri;

        // 쿠키를 막은 클라이언트에는 톰캣이 ;jsessionid=... 를 경로에 붙여 보낸다.
        // 그대로 두면 방문자마다 다른 경로가 되어 집계가 통째로 흩어진다.
        int semicolon = path.indexOf(';');
        if (semicolon >= 0) {
            path = path.substring(0, semicolon);
        }

        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "/";
        }

        return path.length() > MAX_PATH_LENGTH ? path.substring(0, MAX_PATH_LENGTH) : path;
    }

    private boolean shouldCount(HttpServletRequest request, Object handler) {
        /*
         * 정적 리소스는 방문이 아니다.
         *
         * 인터셉터는 DispatcherServlet 을 거치는 요청에 전부 걸리는데, 정적 파일도
         * ResourceHttpRequestHandler 를 통해 그 안에서 처리된다. 그래서 화면 하나를
         * 열 때마다 js·css·이미지 요청 수십 건이 함께 집계됐다.
         *
         * 순 방문자 수는 (날짜, IP) UNIQUE 라 영향이 없지만, 시간대별 접속 건수가
         * 실제의 수십 배로 부풀고 요청마다 DB 쓰기가 한 번씩 더 일어난다.
         *
         * 경로 문자열로 거르지 않고 핸들러 타입으로 판단한다. 정적 경로가 늘어나도
         * 여기를 고칠 필요가 없다.
         */
        if (handler instanceof ResourceHttpRequestHandler) {
            return false;
        }

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return false;
        }

        // 화면이 아닌 요청. 브라우저가 알아서 가져가는 것들이라 사람의 방문이 아니다.
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin") || uri.startsWith("/error")
                || uri.equals("/manifest.json") || uri.equals("/favicon.png")
                || uri.equals("/apple-touch-icon.png") || uri.equals("/robots.txt")
                || uri.equals("/ads.txt") || uri.equals("/sitemap.xml")
                || uri.startsWith("/.well-known")) {
            return false;
        }

        return !isBot(request.getHeader("User-Agent"));
    }

    private boolean isBot(String userAgent) {
        // User-Agent 를 아예 보내지 않는 쪽은 사람이 쓰는 브라우저가 아니다.
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String ua = userAgent.toLowerCase();
        for (String marker : BOT_MARKERS) {
            if (ua.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
