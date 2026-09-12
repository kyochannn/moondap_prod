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

    var el = document.createElement('section');
    el.className = 'resume-banner';
    el.innerHTML =
      '<a class="resume-banner-link" href="' + p.url + '">' +
        '<div class="resume-banner-body">' +
          '<span class="resume-banner-label">진행 중</span>' +
          '<strong class="resume-banner-title"></strong>' +
          '<span class="resume-banner-count">' + done + ' / ' + p.total + '문항</span>' +
          '<div class="resume-banner-track"><div class="resume-banner-bar"></div></div>' +
        '</div>' +
        '<span class="btn btn-primary btn-sm resume-banner-cta">이어서 하기 <i class="bi bi-arrow-right"></i></span>' +
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
