package com.moondap.config;

import com.moondap.common.CommonUtil;
import com.moondap.service.StatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

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
            "embedly", "quora link preview", "bitlybot", "yeti", "daum"
    };

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        if (shouldCount(request)) {
            try {
                statService.recordVisit(CommonUtil.getClientIp(request));
            } catch (Exception e) {
                log.error("방문 집계 실패: uri={}", request.getRequestURI(), e);
            }
        }
        return true;
    }

    private boolean shouldCount(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return false;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/admin") || uri.startsWith("/error")) {
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
