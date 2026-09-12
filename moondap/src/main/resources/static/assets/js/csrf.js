/**
 * CSRF 전역 설정
 *
 * 반드시 jQuery 다음, 나머지 사용자 스크립트보다 먼저 로드되어야 한다.
 * (fragment/script.html 참고)
 *
 * 토큰 출처는 fragment/link.html 의 meta 태그다.
 *   - 일반 폼      : th:action 을 쓰면 Thymeleaf 가 hidden 필드를 자동 주입하므로 대상 아님
 *   - jQuery AJAX  : 아래 $.ajaxSetup 이 모든 변경 요청에 헤더를 자동으로 붙인다
 *   - 네이티브 fetch : window.mdCsrf.headers() 를 호출부에서 직접 펼쳐 쓴다
 */
(function (window, document) {
    'use strict';

    function meta(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? el.getAttribute('content') : null;
    }

    var token = meta('_csrf');
    var headerName = meta('_csrf_header');

    // 토큰을 읽을 필요가 없는 안전한 메서드 (RFC 7231)
    var SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

    function isSafe(method) {
        return SAFE_METHODS.indexOf(String(method || 'GET').toUpperCase()) !== -1;
    }

    /**
     * fetch 호출부에서 사용하는 헤더 객체를 돌려준다.
     *
     *   fetch(url, {
     *       method: 'POST',
     *       headers: Object.assign({ 'Content-Type': 'application/json' }, window.mdCsrf.headers())
     *   })
     *
     * 토큰이 없으면 빈 객체를 돌려주므로 호출부에서 분기할 필요가 없다.
     */
    function headers() {
        var h = {};
        if (token && headerName) {
            h[headerName] = token;
        }
        return h;
    }

    window.mdCsrf = {
        token: token,
        headerName: headerName,
        headers: headers,
        isSafe: isSafe
    };

    if (!token || !headerName) {
        // 레이아웃을 쓰지 않는 독립 페이지(예: user/login.html)는 meta 태그가 없다.
        // 그런 페이지는 폼 전송만 하므로 정상이다.
        return;
    }

    if (window.jQuery) {
        window.jQuery.ajaxSetup({
            beforeSend: function (xhr, settings) {
                if (isSafe(settings.type)) {
                    return;
                }
                // 외부 도메인으로 토큰이 새어나가지 않도록 동일 출처만 허용한다.
                if (settings.crossDomain) {
                    return;
                }
                xhr.setRequestHeader(headerName, token);
            }
        });
    }
})(window, document);
