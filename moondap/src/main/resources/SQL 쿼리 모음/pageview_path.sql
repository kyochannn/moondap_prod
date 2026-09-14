-- 경로별 조회수와 유입 출처
--
-- 지금까지의 통계는 "몇 명이 왔나"(md_visit_daily)와 "언제 왔나"(md_pageview_hourly)만
-- 알 수 있었다. 그래서 하루 90명이 들어와 1~2명만 테스트를 끝내는 상황에서,
-- 그 원인이 "메인만 보고 나가서"인지 "테스트를 시작했다가 중간에 포기해서"인지
-- 구분할 방법이 없었다. 무엇을 고쳐야 할지가 계속 추측이었다.
--
-- 적용:  mysql -u <user> -p moondap < pageview_path.sql
--
-- 두 테이블 모두 개인을 식별할 값이 없다(IP·쿠키 없음, 날짜 단위 합계뿐).
-- 그래서 md_visit_log 와 달리 보유기간 파기 대상이 아니다.

-- 경로별 조회수.
--
-- view_count  : 그 화면이 열린 횟수. 같은 사람이 여러 번 봐도 센다.
-- entry_count : 그중 '바깥에서 들어온' 횟수(Referer 가 없거나 외부 도메인).
--               사이트 안에서 링크를 눌러 이동한 것은 유입이 아니라 빼야,
--               검색·SNS 가 어느 화면으로 사람을 데려오는지가 보인다.
CREATE TABLE IF NOT EXISTS md_pageview_path (
    visit_date  DATE         NOT NULL,

    -- 191자인 것은 utf8mb4 에서 인덱스 한 컬럼이 767바이트를 넘지 않게 하기 위함이다
    -- (191 * 4 = 764). PK 에 들어가므로 VARCHAR(255) 로 두면 구버전에서 생성이 실패한다.
    path        VARCHAR(191) NOT NULL COMMENT '쿼리스트링 제외, 뒤 슬래시 제거',

    view_count  BIGINT       NOT NULL DEFAULT 0,
    entry_count BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (visit_date, path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 유입 출처.
--
-- Referer 의 도메인을 사람이 읽을 수 있는 이름으로 바꿔 저장한다(구글/네이버/직접 유입 …).
-- 전체 URL 을 남기지 않는 이유는 두 가지다. 검색어가 붙은 URL 이 그대로 쌓이면
-- 남의 검색 기록을 보관하는 셈이 되고, URL 단위로는 종류가 너무 많아 집계가 안 된다.
CREATE TABLE IF NOT EXISTS md_referrer_daily (
    visit_date  DATE         NOT NULL,
    source      VARCHAR(100) NOT NULL COMMENT '구글 / 네이버 / 직접 유입 / 그 외는 도메인',
    visit_count BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (visit_date, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
