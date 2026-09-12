/**
 * 숫자가 올라가는 애니메이션.
 *
 * data-count-up 이 붙은 요소를 찾아, 화면에 들어올 때 0 부터 원래 값까지 센다.
 *
 * 최종 값은 서버가 이미 HTML 에 넣어둔 것을 그대로 읽어서 쓴다. 자바스크립트가
 * 막혀 있거나 이 파일이 로드되지 않아도 숫자는 그대로 보인다.
 *
 * 사용:  <strong data-count-up th:text="...">269,544</strong>
 */
(function () {
  'use strict';

  var DURATION_MS = 1400;

  /** 화면에 보이는 문자열에서 숫자만 뽑는다. "269,544" -> 269544 */
  function parseTarget(text) {
    var digits = String(text).replace(/[^0-9]/g, '');
    return digits ? parseInt(digits, 10) : null;
  }

  /**
   * 처음엔 빠르게, 끝에서 천천히 멈춘다.
   * 일정한 속도로 세면 목표에 도달하는 순간이 뚝 끊겨 보인다.
   */
  function easeOut(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  function animate(el, target) {
    var start = null;

    function step(now) {
      if (start === null) start = now;

      var progress = Math.min((now - start) / DURATION_MS, 1);
      var value = Math.round(target * easeOut(progress));

      el.textContent = value.toLocaleString('ko-KR');

      if (progress < 1) {
        requestAnimationFrame(step);
      } else {
        // 반올림 때문에 마지막 값이 1 차이 날 수 있다. 정확한 값으로 맞춘다.
        el.textContent = target.toLocaleString('ko-KR');
      }
    }

    requestAnimationFrame(step);
  }

  function init(el) {
    var target = parseTarget(el.textContent);
    if (target === null || target === 0) return;

    // 움직임을 줄이도록 설정한 사용자에게는 애니메이션 없이 최종 값만 둔다.
    var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce || !window.requestAnimationFrame) return;

    // 화면 밖에 있는 숫자는 볼 사람이 없는데 먼저 다 세어버리면,
    // 정작 스크롤해서 도착했을 때는 아무 일도 일어나지 않는다.
    if (!window.IntersectionObserver) {
      animate(el, target);
      return;
    }

    el.textContent = '0';

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        observer.unobserve(entry.target);   // 한 번만 센다
        animate(entry.target, target);
      });
    }, { threshold: 0.4 });

    observer.observe(el);
  }

  document.addEventListener('DOMContentLoaded', function () {
    Array.prototype.forEach.call(document.querySelectorAll('[data-count-up]'), init);
  });
})();
