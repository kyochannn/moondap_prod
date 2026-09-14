/**
 * 문답 이벤트 추적 공통 모듈 (GA4)
 *
 * 화면 코드는 gtag 를 직접 부르지 않고 여기 있는 mdTrack 만 쓴다. 이유는 두 가지다.
 *
 *  1) 측정 ID(analytics.ga4-id)가 비어 있으면 레이아웃이 gtag 스니펫 자체를 렌더하지
 *     않는다. 로컬·테스트에서 운영 통계를 오염시키지 않기 위한 의도된 동작이라,
 *     화면 코드가 gtag 를 직접 부르면 dev 에서 ReferenceError 로 죽는다.
 *  2) 광고 차단기가 googletagmanager.com 을 막는 비율이 국내에서도 낮지 않다.
 *     추적이 막히는 것은 감수할 수 있지만, 그것 때문에 공유 버튼이 동작하지 않는 것은
 *     감수할 수 없다. 그래서 모든 호출을 try/catch 로 감싸 조용히 넘긴다.
 *
 * 수집하는 이벤트는 네 개이고, 그 조합이 이 서비스의 성장 공식이다.
 *
 *   test_start     소개 → 시작 전환. 낮으면 소개글·썸네일 문제다.
 *   test_complete  실제 응시 완료. 시작 대비 낮으면 문항이 길거나 지루한 것이다.
 *   share_click    결과를 받은 사람이 공유를 눌렀다.        ← 바이럴 계수의 분자
 *   share_inflow   그 공유 링크를 타고 새 방문자가 들어왔다. ← 바이럴 계수의 분모
 *
 * share_click ÷ test_complete 와 share_inflow ÷ share_click 이 1 에 가까워질수록
 * 검색 유입 없이도 스스로 도는 서비스가 된다. 심리테스트는 검색보다 이 루프로 큰다.
 */
(function () {
    'use strict';

    /** 공유 링크에 붙는 유입 채널 파라미터. share_inflow 집계의 유일한 근거다. */
    var REF_PARAM = 'ref';

    /**
     * 이벤트 한 건 전송. 실패는 모두 삼킨다.
     *
     * @param {string} name   GA4 이벤트 이름(snake_case)
     * @param {Object} [params] 이벤트 파라미터
     */
    window.mdTrack = function (name, params) {
        try {
            if (typeof window.gtag === 'function') {
                window.gtag('event', name, params || {});
            }
        } catch (e) {
            // 추적 실패가 화면 기능을 막아서는 안 된다. 로그도 남기지 않는다
            // (차단기를 쓰는 방문자의 콘솔이 매번 빨개지는 것을 피한다).
        }
    };

    /**
     * 공유할 URL 에 유입 채널을 붙인다.
     *
     * 이걸 붙이지 않으면 공유가 몇 번 일어났는지(share_click)는 알아도 그래서 몇 명이
     * 들어왔는지(share_inflow)를 알 수 없다. 분자만 있고 분모가 없는 상태가 된다.
     *
     * canonical 은 SeoMetaInterceptor 가 쿼리스트링을 버리고 만들므로 이 파라미터가
     * 색인을 오염시키지는 않는다. 다만 주소창에는 남으므로 아래에서 지운다.
     *
     * @param {string} url     원본 URL
     * @param {string} channel 'kakao' | 'link' 등
     */
    window.mdWithRef = function (url, channel) {
        if (!url || !channel) {
            return url;
        }
        try {
            var parsed = new URL(url, window.location.origin);
            parsed.searchParams.set(REF_PARAM, channel);
            return parsed.toString();
        } catch (e) {
            // URL 생성자를 못 쓰는 환경이면 추적을 포기하고 원본을 그대로 쓴다.
            // 공유 자체가 실패하는 것보다 낫다.
            return url;
        }
    };

    /**
     * 공유 클릭 기록. share-service.js 의 공유 함수들이 한 곳에서 부른다.
     *
     * 페이지마다 따로 붙이지 않는 이유: 공유 버튼은 소개·결과·밸런스게임에 흩어져
     * 있는데 실제 동작은 전부 shareKakaoCommon / copyLinkCommon 을 지난다.
     * 그 길목 하나만 잡으면 새 화면이 생겨도 자동으로 집계된다.
     */
    window.mdTrackShare = function (channel, extra) {
        var params = {
            share_channel: channel,
            content_path: window.location.pathname
        };
        if (extra) {
            Object.keys(extra).forEach(function (k) {
                params[k] = extra[k];
            });
        }
        window.mdTrack('share_click', params);
    };

    /**
     * 공유 링크를 타고 들어온 방문을 기록하고, 주소창에서 ref 를 지운다.
     *
     * 지우는 이유: 이 상태로 방문자가 주소를 다시 복사해 퍼뜨리면 최초 채널이 실제와
     * 무관하게 계속 따라다닌다. 또 GA4 페이지 리포트에서 같은 문서가 ref 값만큼
     * 쪼개져 보인다. replaceState 라 뒤로가기 이력은 건드리지 않는다.
     *
     * 주의: gtag 의 page_view 는 이 시점보다 먼저 나가므로 그 한 건에는 ref 가 남는다.
     * 채널 집계는 page_view 가 아니라 아래 share_inflow 이벤트를 기준으로 본다.
     */
    function trackInflow() {
        var params;
        try {
            params = new URLSearchParams(window.location.search);
        } catch (e) {
            return;
        }

        var channel = params.get(REF_PARAM);
        if (!channel) {
            return;
        }

        window.mdTrack('share_inflow', {
            share_channel: channel,
            landing_path: window.location.pathname
        });

        try {
            params.delete(REF_PARAM);
            var query = params.toString();
            window.history.replaceState(
                null,
                '',
                window.location.pathname + (query ? '?' + query : '') + window.location.hash
            );
        } catch (e) {
            // 주소 정리에 실패해도 집계는 이미 끝났다. 그냥 둔다.
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', trackInflow);
    } else {
        trackInflow();
    }
})();
