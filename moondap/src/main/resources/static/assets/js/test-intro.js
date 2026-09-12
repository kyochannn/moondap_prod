/**
 * 테스트 소개 화면의 하단 고정 CTA.
 *
 * 본문의 「테스트 시작」 버튼이 화면 밖으로 나가면 하단 바를 띄우고, 다시 보이면 숨긴다.
 *
 * 푸터가 보일 때도 숨기도록 했다가 되돌렸다. 이 사이트의 푸터는 440px 가량으로 길어
 * 스크롤 초반부터 화면에 걸치고, 그러면 바가 거의 나타나지 않는다.
 * 대신 푸터 아래쪽에 바 높이만큼 여백을 둬서(test-intro.css) 가려지는 내용이 없게 했다.
 */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    var bar = document.getElementById('introStickyCta');
    var inlineStart = document.querySelector('.intro-actions .intro-start');
    if (!bar || !inlineStart) return;

    // IntersectionObserver 가 없는 구형 브라우저에서는 그냥 숨긴 채 둔다.
    // 본문 버튼은 그대로 있으므로 기능이 사라지지는 않는다.
    if (!('IntersectionObserver' in window)) return;

    new IntersectionObserver(function (entries) {
      var show = !entries[0].isIntersecting;
      bar.classList.toggle('is-visible', show);
      bar.setAttribute('aria-hidden', show ? 'false' : 'true');
    }).observe(inlineStart);

    // 고정 바는 본문 버튼과 똑같이 동작해야 한다. 동작을 복제하지 않고
    // 본문 버튼을 그대로 눌러, 시작 로직이 한 곳에만 있도록 한다.
    var barBtn = bar.querySelector('.btn');
    if (barBtn) {
      barBtn.addEventListener('click', function () {
        inlineStart.click();
      });
    }
  });
})();
