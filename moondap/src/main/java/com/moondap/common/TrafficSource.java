package com.moondap.common;

import java.net.URI;
import java.util.Locale;

/**
 * Referer 헤더를 유입 출처 이름으로 바꾼다.
 *
 * <p>전체 URL 을 그대로 저장하지 않는다. 검색 엔진에서 온 Referer 에는 검색어가 붙어 있어서
 * 그대로 쌓으면 남의 검색 기록을 보관하게 되고, URL 단위로는 종류가 너무 많아 집계도 안 된다.
 * 도메인만 남기고 사람이 읽을 수 있는 이름으로 바꾼다.
 *
 * <p>사이트 안에서의 이동은 유입이 아니므로 {@code null} 을 돌려준다. 이걸 구분하지 않으면
 * 메인 → 테스트로 넘어간 클릭이 전부 "유입"이 되어, 검색·SNS 가 실제로 데려오는 사람 수가
 * 묻혀 버린다.
 */
public final class TrafficSource {

    /** 주소를 직접 입력했거나, 브라우저·앱이 Referer 를 떼고 보낸 경우. */
    public static final String DIRECT = "직접 유입";

    /** md_referrer_daily.source 컬럼 길이. */
    private static final int MAX_LENGTH = 100;

    /**
     * 알려진 출처.
     *
     * <p>마커가 {@code .} 으로 끝나면 부분 일치로, 아니면 도메인 전체 일치로 본다.
     * {@code x.com} 을 부분 일치로 두면 {@code box.com} 까지 X 로 잡히기 때문이다.
     */
    private static final String[][] KNOWN = {
            {"google.", "구글"},
            {"naver.", "네이버"},
            {"daum.", "다음"},
            {"kakao.", "카카오"},
            {"bing.", "빙"},
            {"yahoo.", "야후"},
            {"duckduckgo.", "덕덕고"},
            {"zum.com", "줌"},
            {"instagram.", "인스타그램"},
            {"facebook.", "페이스북"},
            {"threads.", "스레드"},
            {"youtube.", "유튜브"},
            {"youtu.be", "유튜브"},
            {"tiktok.", "틱톡"},
            {"twitter.", "X(트위터)"},
            {"x.com", "X(트위터)"},
            {"t.co", "X(트위터)"},
            {"reddit.", "레딧"},
            {"pinterest.", "핀터레스트"},
            {"tistory.", "티스토리"},
            {"blog.me", "네이버 블로그"},
            {"band.us", "밴드"},
            {"dcinside.", "디시인사이드"},
            {"fmkorea.", "에펨코리아"},
            {"theqoo.net", "더쿠"},
            {"instiz.net", "인스티즈"},
            // AI 챗봇을 통해 들어오는 유입. 검색과 성격이 달라 따로 본다.
            {"chatgpt.", "ChatGPT"},
            {"openai.", "ChatGPT"},
            {"perplexity.", "Perplexity"},
            {"claude.ai", "Claude"},
    };

    private TrafficSource() {
    }

    /**
     * Referer 를 출처 이름으로 바꾼다.
     *
     * @param referrer Referer 헤더 값. 없으면 {@code null}
     * @param selfHost 이 사이트의 호스트 ({@code request.getServerName()})
     * @return 출처 이름. <b>사이트 내부 이동이면 {@code null}</b>
     */
    public static String classify(String referrer, String selfHost) {
        if (referrer == null || referrer.isBlank()) {
            return DIRECT;
        }

        String host = hostOf(referrer);
        if (host == null) {
            // 해석되지 않는 Referer(상대 경로, 깨진 값)는 판단 근거가 없다.
            // 내부 이동으로 단정하면 유입이 통째로 사라지므로 직접 유입으로 둔다.
            return DIRECT;
        }
        if (isSelf(host, selfHost)) {
            return null;
        }

        for (String[] entry : KNOWN) {
            if (matches(host, entry[0])) {
                return entry[1];
            }
        }
        return host.length() > MAX_LENGTH ? host.substring(0, MAX_LENGTH) : host;
    }

    /** Referer 의 호스트. {@code www.} 를 떼고 소문자로 맞춘다. */
    private static String hostOf(String referrer) {
        try {
            String host = URI.create(referrer.trim()).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            // 깨진 Referer 를 보내는 클라이언트가 실제로 있다. 집계 때문에 요청이
            // 실패하면 안 되므로 여기서 삼킨다.
            return null;
        }
    }

    /**
     * 우리 사이트인지.
     *
     * <p>서브도메인과 {@code www} 를 같은 사이트로 본다. 운영은 {@code moondap.com},
     * 로컬은 {@code localhost} 라 호스트를 상수로 박지 않고 요청에서 받는다.
     */
    private static boolean isSelf(String host, String selfHost) {
        if (selfHost == null || selfHost.isBlank()) {
            return false;
        }
        String self = selfHost.toLowerCase(Locale.ROOT);
        if (self.startsWith("www.")) {
            self = self.substring(4);
        }
        return host.equals(self)
                || host.endsWith("." + self)
                || self.endsWith("." + host);
    }

    private static boolean matches(String host, String marker) {
        return marker.endsWith(".")
                ? host.contains(marker)
                : host.equals(marker) || host.endsWith("." + marker);
    }
}
