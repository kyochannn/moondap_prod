-- 방문자 수의 정확도를 확인하고 높이기 위한 테이블들
--
-- 적용:  mysql -u <user> -p moondap < visit_quality.sql
--
-- 지금까지 방문자 수는 (날짜, IP) 하나로만 셌다. 이 방식은 두 방향으로 틀린다.
--   · 회사·학교·통신사 NAT 처럼 여러 명이 한 IP 를 쓰면 → 여러 명이 1명으로 합쳐진다
--   · 이동 중 모바일은 IP 가 바뀌므로          → 1명이 여러 명으로 세어진다
-- 어느 쪽으로 얼마나 틀리는지조차 알 수 없었다. 기준이 하나뿐이면 비교 대상이 없다.
--
-- 그래서 IP 기준은 그대로 두고 쿠키 기준을 하나 더 만든다. 기존 지표를 바꾸지 않는 것이
-- 중요하다 — 바꾸면 2026-05-03 부터의 기록과 비교가 끊긴다.

-- ── 1. 쿠키 기준 순 방문자 ────────────────────────────────────
--
-- md_visit_log 의 IP 자리에 익명 쿠키(md_anon)를 넣은 것과 같은 구조다.
--
-- 이 표에는 '요청에 쿠키가 실려 온' 방문만 들어간다. 방금 발급한 쿠키는 넣지 않는다.
-- 쿠키를 저장하지 않는 클라이언트(주로 자동화 도구)는 요청마다 새 UUID 를 받게 되는데,
-- 발급 시점에 세면 그 한 대가 하루 수백 명으로 둔갑한다. 되돌려 보내온 쿠키만 세면
-- 그런 클라이언트는 한 번도 잡히지 않는다.
--
-- 대신 '생애 첫 방문에 한 페이지만 보고 떠난 사람'은 빠진다. 그쪽은 IP 기준이 잡으므로
-- 두 숫자를 나란히 보는 것이 이 구조의 목적이다.
CREATE TABLE IF NOT EXISTS md_visit_cookie (
    visit_date    DATE        NOT NULL,
    anon_id       VARCHAR(64) NOT NULL COMMENT 'md_anon 쿠키 값 (임의 UUID)',
    first_seen_at DATETIME             DEFAULT NULL COMMENT '그날 처음 들어온 시각',

    PRIMARY KEY (visit_date, anon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- md_visit_daily 에 카운터 컬럼을 두지 않는다. 카운터는 언젠가 행 수와 어긋나는데,
-- 그때 어느 쪽이 맞는지 알 방법이 없다. PK(visit_date, anon_id) 라 날짜별 COUNT 는
-- 인덱스만 읽으므로 굳이 따로 세어 둘 이유가 없다.

-- ── 2. User-Agent 계측 ───────────────────────────────────────
--
-- 봇 판정은 User-Agent 조각을 손으로 고른 목록에 의존한다. 실제로 "daum" 이라는
-- 조각 하나 때문에 다음 앱 인앱 브라우저(DaumApps/6.9.x)를 쓰는 실제 방문자가
-- 통째로 누락된 적이 있다. 목록을 고친 뒤에도 '지금 제대로 거르고 있는가'를
-- 확인할 방법이 없었다 — User-Agent 를 어디에도 남기지 않았기 때문이다.
--
-- 걸러낸 것과 사람으로 센 것을 모두 남긴다. 걸러낸 목록에 멀쩡한 브라우저가
-- 올라와 있으면 그것이 오탐이다.
--
-- IP 와 함께 두지 않는다. User-Agent 만으로는 개인을 식별할 수 없지만 IP 와 묶는
-- 순간 지문이 된다. 여기에는 날짜와 문자열과 횟수만 있다.
CREATE TABLE IF NOT EXISTS md_user_agent_daily (
    visit_date DATE         NOT NULL,

    -- 191자인 것은 utf8mb4 에서 인덱스 한 컬럼이 767바이트를 넘지 않게 하기 위함이다.
    user_agent VARCHAR(191) NOT NULL,

    counted    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=사람으로 셈, 0=봇으로 걸러냄',
    hit_count  BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (visit_date, user_agent),
    KEY idx_ua_counted (visit_date, counted, hit_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 3. 누락분 보정 ───────────────────────────────────────────
--
-- pageview_path.sql 의 두 번째 테이블이 운영에 만들어지지 않은 상태였다
-- (2026-09-15 확인). IF NOT EXISTS 라 이미 있으면 아무 일도 하지 않는다.
CREATE TABLE IF NOT EXISTS md_referrer_daily (
    visit_date  DATE         NOT NULL,
    source      VARCHAR(100) NOT NULL COMMENT '구글 / 네이버 / 직접 유입 / 그 외는 도메인',
    visit_count BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (visit_date, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
