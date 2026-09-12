/**
 * HTML 이스케이프 전역 유틸
 *
 * 클라이언트에서 문자열을 조립해 DOM 에 넣을 때(innerHTML, jQuery append/html)
 * 사용자 입력은 반드시 이 함수를 거쳐야 한다.
 *
 * 전역으로 둔 이유: 예전에는 selectBalanceGame.html 안에만 escapeHtml 이 정의돼 있었고,
 * 같은 데이터를 그리는 selectBalanceGameList.html 에는 정의조차 없어서
 * 게임 제목이 이스케이프 없이 삽입됐다(저장형 XSS).
 * 화면마다 복사해 두면 이런 누락이 반복되므로 한 곳에서 제공한다.
 *
 * fragment/script.html 에서 jQuery 직후에 로드한다.
 */
(function (window) {
    'use strict';

    var MAP = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };

    /**
     * HTML 본문/속성값에 넣어도 안전한 문자열로 바꾼다.
     * null·undefined 는 빈 문자열이 된다.
     */
    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value).replace(/[&<>"']/g, function (ch) {
            return MAP[ch];
        });
    }

    window.escapeHtml = escapeHtml;
})(window);
