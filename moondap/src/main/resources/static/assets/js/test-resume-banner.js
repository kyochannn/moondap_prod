/**
 * 메인 화면의 "이어서 하기" 배너.
 *
 * 진행 중인 테스트가 있을 때만 히어로 바로 아래에 나타난다. 없으면 아무것도
 * 그리지 않으므로, 처음 온 사용자의 첫 화면은 지금과 똑같다.
 *
 * 진행 기록은 test-progress.js 가 localStorage 에 남긴다.
 */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    if (!window.TestProgress) return;

    var p = TestProgress.latest();
    if (!p || !p.url) return;

    var hero = document.getElementById('hero-intro');
    if (!hero) return;

    var done = p.index;
    var pct = Math.round((done / p.total) * 100);

    // 다 풀었지만 결과를 못 본 기록(광고 대기 중 이탈)은 "이어서 하기"가 아니다.
    // 남은 문항이 없는데 이어서 하라고 하면 또 풀어야 하는 줄 알고 누르지 않는다.
    var label = p.pending ? '결과 대기' : '진행 중';
    var count = p.pending ? '문항을 모두 풀었어요' : done + ' / ' + p.total + '문항';
    var cta = p.pending ? '결과 확인하기' : '이어서 하기';

    var el = document.createElement('section');
    el.className = 'resume-banner';
    el.innerHTML =
      '<a class="resume-banner-link" href="' + p.url + '">' +
        '<div class="resume-banner-body">' +
          '<span class="resume-banner-label">' + label + '</span>' +
          '<strong class="resume-banner-title"></strong>' +
          '<span class="resume-banner-count">' + count + '</span>' +
          '<div class="resume-banner-track"><div class="resume-banner-bar"></div></div>' +
        '</div>' +
        '<span class="btn btn-primary btn-sm resume-banner-cta">' + cta + ' <i class="bi bi-arrow-right"></i></span>' +
      '</a>' +
      '<button type="button" class="resume-banner-close" aria-label="이어하기 숨기기">' +
        '<i class="bi bi-x-lg"></i></button>';

    // 제목은 사용자 입력이라 textContent 로 넣는다.
    el.querySelector('.resume-banner-title').textContent = p.title || '진행 중인 테스트';
    el.querySelector('.resume-banner-bar').style.width = pct + '%';

    el.querySelector('.resume-banner-close').addEventListener('click', function () {
      TestProgress.clear(p.id);
      el.remove();
    });

    hero.insertAdjacentElement('afterend', el);
  });
})();
