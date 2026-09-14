/**
 * 진행 중인 테스트 저장·복원 (localStorage)
 *
 * 문항을 풀다 이탈하면 답변이 전부 사라져서, 다시 온 사용자는 처음부터 해야 했다.
 * 8문항 중 5개를 푼 사람은 이미 몰입한 상태라 되돌리기 쉬운 대상이다.
 *
 * 저장은 질문 화면이, 복원 안내는 메인 화면(test-resume-banner.js)이 맡는다.
 * 서버에 아무것도 보내지 않으므로 비로그인 사용자도 그대로 동작한다.
 *
 * 상태는 두 가지다.
 *   진행 중  index < total  — 아직 덜 푼 상태. save() 가 남긴다.
 *   결과 대기 pending: true — 다 풀었지만 결과를 아직 못 본 상태. savePending() 이 남긴다.
 */
window.TestProgress = (function () {
  'use strict';

  var KEY = 'moondap:test-progress';
  var MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000; // 일주일 지난 기록은 이어하기를 권하지 않는다.

  // 시크릿 모드 등 localStorage 를 못 쓰는 환경에서도 페이지가 깨지지 않아야 한다.
  function readAll() {
    try {
      var raw = localStorage.getItem(KEY);
      if (!raw) return {};
      var all = JSON.parse(raw);
      return all && typeof all === 'object' ? all : {};
    } catch (e) {
      return {};
    }
  }

  function writeAll(all) {
    try {
      localStorage.setItem(KEY, JSON.stringify(all));
    } catch (e) {
      /* 용량 초과·권한 없음 — 이어하기를 포기할 뿐 진행에는 지장이 없다. */
    }
  }

  function isFresh(entry) {
    return entry && entry.savedAt && (Date.now() - entry.savedAt) < MAX_AGE_MS;
  }

  return {
    /**
     * @param {object} p - { id, url, title, index, total, answers }
     *   id    저장 구분자 (테스트 키, 에겐테토는 'egenTeto:<성별>')
     *   url   이어서 열 주소
     *   index 다음에 풀어야 할 문항 번호 (0부터)
     */
    save: function (p) {
      if (!p || !p.id || !p.total) return;
      // 첫 문항을 풀기 전이거나 다 푼 상태는 "이어할 것"이 아니다.
      if (p.index <= 0 || p.index >= p.total) {
        this.clear(p.id);
        return;
      }
      var all = readAll();
      all[p.id] = {
        id: p.id, url: p.url, title: p.title,
        index: p.index, total: p.total,
        answers: p.answers || [], savedAt: Date.now(),
      };
      writeAll(all);
    },

    /**
     * 다 풀었지만 아직 결과를 못 본 상태.
     *
     * 마지막 문항 뒤에는 광고·대기 화면이 뜨고, 광고는 현재 탭에서 열린다.
     * 여기서 사용자가 페이지를 떠나면 답변은 JS 메모리와 hidden input 에만
     * 있었으므로 전부 사라졌고, 돌아온 사람은 처음부터 다시 풀어야 했다.
     * 다 푼 사람을 잃는 것이라 중간 이탈보다 손해가 크다.
     *
     * save() 는 index >= total 을 "이어할 것이 없음"으로 보고 지우므로 따로 둔다.
     * 이 기록은 결과 화면이 실제로 열릴 때 clear() 로 지운다.
     */
    savePending: function (p) {
      if (!p || !p.id || !p.total) return;
      var all = readAll();
      all[p.id] = {
        id: p.id, url: p.url, title: p.title,
        index: p.total, total: p.total,
        answers: p.answers || [], pending: true, savedAt: Date.now(),
      };
      writeAll(all);
    },

    load: function (id) {
      var entry = readAll()[id];
      if (!isFresh(entry)) {
        if (entry) this.clear(id);
        return null;
      }
      return entry;
    },

    /** 가장 최근에 풀던 것 하나. 메인 화면 배너가 쓴다. */
    latest: function () {
      var all = readAll();
      var best = null;
      Object.keys(all).forEach(function (k) {
        var e = all[k];
        if (!isFresh(e)) return;
        if (!best || e.savedAt > best.savedAt) best = e;
      });
      return best;
    },

    clear: function (id) {
      var all = readAll();
      if (!(id in all)) return;
      delete all[id];
      writeAll(all);
    },
  };
})();
