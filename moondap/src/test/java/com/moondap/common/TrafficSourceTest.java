package com.moondap.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 유입 출처 판정.
 *
 * <p>방문자 수만으로는 하루 90명이 들어와 1~2명만 참여하는 이유를 알 수 없었다.
 * 그 첫 단추가 "어디서 오는가"인데, 여기서 내부 이동을 걸러내지 못하면
 * 메인 → 테스트 클릭이 전부 유입으로 잡혀 지표가 통째로 무의미해진다.
 */
class TrafficSourceTest {

    private static final String SELF = "moondap.com";

    @Test
    @DisplayName("사이트 안에서의 이동은 유입이 아니다")
    void internalIsNotEntry() {
        assertThat(TrafficSource.classify("https://moondap.com/test/abc", SELF)).isNull();
        // www 유무와 서브도메인은 같은 사이트로 본다.
        assertThat(TrafficSource.classify("https://www.moondap.com/", SELF)).isNull();
        assertThat(TrafficSource.classify("https://m.moondap.com/", SELF)).isNull();
        // 요청이 www 로 들어온 경우도 마찬가지다.
        assertThat(TrafficSource.classify("https://moondap.com/", "www.moondap.com")).isNull();
    }

    @Test
    @DisplayName("로컬에서도 내부 이동으로 판정된다")
    void internalOnLocalhost() {
        // 호스트를 상수로 박으면 로컬 확인 때마다 유입이 잘못 쌓인다.
        assertThat(TrafficSource.classify("http://localhost:8080/", "localhost")).isNull();
    }

    @Test
    @DisplayName("Referer 가 없으면 직접 유입이다")
    void noRefererIsDirect() {
        assertThat(TrafficSource.classify(null, SELF)).isEqualTo(TrafficSource.DIRECT);
        assertThat(TrafficSource.classify("  ", SELF)).isEqualTo(TrafficSource.DIRECT);
    }

    @Test
    @DisplayName("깨진 Referer 는 유입을 없애지 않고 직접 유입으로 둔다")
    void brokenRefererIsDirect() {
        // 내부 이동으로 단정하면 실제 유입이 통째로 사라진다. 반대로 두는 편이 안전하다.
        assertThat(TrafficSource.classify("not a url", SELF)).isEqualTo(TrafficSource.DIRECT);
        assertThat(TrafficSource.classify("/just/a/path", SELF)).isEqualTo(TrafficSource.DIRECT);
    }

    @Test
    @DisplayName("주요 검색·SNS 는 이름으로 묶인다")
    void labelsKnownSources() {
        assertThat(TrafficSource.classify("https://m.search.naver.com/search.naver?query=x", SELF))
                .isEqualTo("네이버");
        assertThat(TrafficSource.classify("https://www.google.co.kr/", SELF)).isEqualTo("구글");
        assertThat(TrafficSource.classify("https://search.daum.net/search?q=x", SELF)).isEqualTo("다음");
        assertThat(TrafficSource.classify("https://l.instagram.com/?u=x", SELF)).isEqualTo("인스타그램");
        assertThat(TrafficSource.classify("https://t.co/abc", SELF)).isEqualTo("X(트위터)");
    }

    @Test
    @DisplayName("검색어가 붙은 Referer 도 도메인만 남는다")
    void doesNotKeepQueryString() {
        String label = TrafficSource.classify(
                "https://www.google.com/search?q=%EC%97%B0%EC%95%A0+%ED%85%8C%EC%8A%A4%ED%8A%B8", SELF);

        // 전체 URL 을 저장하면 남의 검색 기록을 보관하는 셈이 된다.
        assertThat(label).isEqualTo("구글");
        assertThat(label).doesNotContain("q=");
    }

    @Test
    @DisplayName("짧은 도메인 마커가 엉뚱한 곳에 붙지 않는다")
    void doesNotOvermatchShortDomains() {
        // "x.com" 을 부분 일치로 두면 box.com 까지 X 로 잡힌다.
        assertThat(TrafficSource.classify("https://box.com/a", SELF)).isEqualTo("box.com");
        assertThat(TrafficSource.classify("https://mobile.x.com/a", SELF)).isEqualTo("X(트위터)");
    }

    @Test
    @DisplayName("모르는 곳은 도메인 그대로 남긴다")
    void keepsUnknownHost() {
        // 목록에 없는 커뮤니티에서 링크가 퍼지는 일이 실제로 있다. 'ETC' 로 뭉치면
        // 어디서 터졌는지 확인할 방법이 사라진다.
        assertThat(TrafficSource.classify("https://www.example-cafe.co.kr/board/1", SELF))
                .isEqualTo("example-cafe.co.kr");
    }
}
