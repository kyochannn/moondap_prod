-- 테이블 이름 정리
--
-- 이름 규칙이 세 갈래로 갈라져 있어서, 이름만 보고는 그 테이블이 무엇인지도
-- 어느 기능에 속하는지도 알 수 없었다.
--
--   * 접두사가 md_ / balance_ / site_ / 없음 네 가지로 섞여 있었다.
--   * 단수·복수가 섞여 있었다(md_tests vs md_test_history).
--   * 이름이 내용과 어긋난 것이 있었다. balance_questions 는 밸런스 게임 본체인데
--     "질문" 이라 읽혀서, 심리테스트의 md_test_questions 와 같은 층위로 오해된다.
--   * md_test_category 와 md_reports 는 이름상 심리테스트 전용처럼 보이지만
--     실제로는 밸런스 게임도 함께 쓰는 공용 테이블이다(BalanceGameMapper 가 조인하고,
--     신고는 COMMENT/BALANCE/TEST 를 모두 받는다). 이름이 사용처를 숨기고 있었다.
--
-- 그리고 이름이 서로 반대로 읽히는 쌍이 있었다. 이게 제일 위험했다.
--
--   * md_test_results 는 사용자가 받은 결과가 아니라, 제작자가 미리 만들어 둔
--     점수 구간별 결과지 정의다(result_title / min_score / max_score).
--   * 사용자가 실제로 받은 결과는 md_test_history 에 있다("내 결과 보관함").
--
--     이름만 보면 정확히 뒤바뀌어 읽힌다. 그래서 앞의 것은 md_test_result_type,
--     뒤의 것은 md_test_play 로 바꿔 "정의" 와 "기록" 이 구분되게 했다.
--     egen_teto_test_result 도 같은 성격의 기록이므로 md_egenteto_play 로 맞췄다.
--
--   * site_visit_hourly 는 이름은 방문인데 실제로 쌓이는 값은 view_count,
--     즉 순방문자가 아니라 페이지뷰다(visit_hourly.sql 의 주석에도 그렇게 적혀 있다).
--     일자별 md_visit_daily 의 visit_count(순방문)와 단위가 달라서, 같은 "visit"
--     이름을 쓰면 두 숫자를 같은 것으로 착각하게 된다. md_pageview_hourly 로 나눴다.
--
-- 전체적으로는 md_ 를 전 테이블 공통 네임스페이스로 삼고, 그 뒤에 기능 묶음
-- (test / balance / visit)을 두고, 전부 단수로 통일했다. 정렬만 해도 기능별로 모인다.
--
-- 컬럼 이름은 이번 범위가 아니다. md_egenteto_play 의 userNo 처럼 snake_case 를
-- 벗어난 컬럼이 남아 있다(mapUnderscoreToCamelCase 설정과도 어긋난다).
--
-- 적용:  mysql -u <user> -p moondap < table_rename.sql
--
-- ---------------------------------------------------------------------------
-- 배포 순서 (이 순서를 지켜야 한다)
--
--   1) 1단계 + 2단계를 DB 에 적용한다. 이 시점에 구 이름은 뷰로 살아 있으므로
--      **운영에 떠 있는 구버전 WAR 가 그대로 계속 동작한다.** 무중단이다.
--   2) 새 WAR 를 배포한다.
--   3) 며칠 지켜본 뒤 3단계로 뷰를 지운다.
--
-- 뷰를 두는 이유는 롤백 때문이다. RENAME 만 하고 배포하면, DB 를 바꾼 순간부터
-- 새 WAR 가 뜰 때까지 구버전이 500 을 뱉고, 배포가 실패해 구버전으로 되돌릴 때도
-- 역방향 RENAME 을 먼저 실행해야 한다. 그 사이가 전부 장애 구간이다.
-- ---------------------------------------------------------------------------


-- === 1단계. 이름 변경 ===
--
-- RENAME TABLE 은 여러 개를 한 문장에 쓰면 원자적으로 처리된다. 나눠 쓰면
-- 중간에 실패했을 때 절반만 바뀐 상태로 남으므로 반드시 한 문장으로 둔다.
-- 외래키(md_balance_comment.fk_comment_question 등)는 MySQL/MariaDB 가
-- 새 이름을 가리키도록 알아서 고쳐준다.

RENAME TABLE
    -- 심리테스트 — 제작자가 만드는 정의
    md_tests              TO md_test,
    md_test_questions     TO md_test_question,
    md_test_results       TO md_test_result_type,
    -- 심리테스트 — 사용자가 남기는 기록
    md_test_history       TO md_test_play,
    egen_teto_test_result TO md_egenteto_play,
    -- 밸런스 게임
    balance_questions     TO md_balance_game,
    balance_comments      TO md_balance_comment,
    balance_comment_like  TO md_balance_comment_like,
    balance_vote_log      TO md_balance_vote,
    -- 콘텐츠 공용 (심리테스트 · 밸런스 게임이 함께 쓴다)
    md_test_category      TO md_content_category,
    md_reports            TO md_content_report,
    -- 회원 · 채번
    md_users              TO md_user,
    id_sequence           TO md_id_sequence,
    -- 접속 통계
    site_visit_log        TO md_visit_log,
    site_statistics       TO md_visit_daily,
    site_visit_hourly     TO md_pageview_hourly;


-- === 2단계. 구 이름 호환 뷰 ===
--
-- 단일 테이블 SELECT * 뷰는 갱신 가능(updatable)하고 삽입 가능(insertable)해서
-- INSERT/UPDATE/DELETE 가 모두 통과한다. 구버전 WAR 가 이 뷰로 쓴 것은
-- 새 이름 테이블에 그대로 들어간다.

CREATE OR REPLACE VIEW md_tests              AS SELECT * FROM md_test;
CREATE OR REPLACE VIEW md_test_questions     AS SELECT * FROM md_test_question;
CREATE OR REPLACE VIEW md_test_results       AS SELECT * FROM md_test_result_type;
CREATE OR REPLACE VIEW md_test_history       AS SELECT * FROM md_test_play;
CREATE OR REPLACE VIEW egen_teto_test_result AS SELECT * FROM md_egenteto_play;
CREATE OR REPLACE VIEW balance_questions     AS SELECT * FROM md_balance_game;
CREATE OR REPLACE VIEW balance_comments      AS SELECT * FROM md_balance_comment;
CREATE OR REPLACE VIEW balance_comment_like  AS SELECT * FROM md_balance_comment_like;
CREATE OR REPLACE VIEW balance_vote_log      AS SELECT * FROM md_balance_vote;
CREATE OR REPLACE VIEW md_test_category      AS SELECT * FROM md_content_category;
CREATE OR REPLACE VIEW md_reports            AS SELECT * FROM md_content_report;
CREATE OR REPLACE VIEW md_users              AS SELECT * FROM md_user;
CREATE OR REPLACE VIEW id_sequence           AS SELECT * FROM md_id_sequence;
CREATE OR REPLACE VIEW site_visit_log        AS SELECT * FROM md_visit_log;
CREATE OR REPLACE VIEW site_statistics       AS SELECT * FROM md_visit_daily;
CREATE OR REPLACE VIEW site_visit_hourly     AS SELECT * FROM md_pageview_hourly;


-- === 검증 ===
--
-- 뷰가 진짜 쓰기까지 되는지 여기서 확인하고 넘어간다. 특히 방문 집계 3개는
-- INSERT ... ON DUPLICATE KEY UPDATE 를 쓰는데, 이 구문의 뷰 지원은 서버 버전을
-- 탄다. 운영은 MariaDB 이므로 반드시 운영에서 이 블록을 돌려보고,
-- 실패하면 뷰에 기대지 말고 아래 "뷰 없이 가는 경우" 로 전환한다.

-- (1) 테이블 16개가 새 이름으로 있고, 구 이름 16개가 VIEW 로 있어야 한다.
SELECT TABLE_TYPE, COUNT(*) AS cnt
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'moondap'
 GROUP BY TABLE_TYPE;

-- (2) 구 이름 뷰로 INSERT ... ON DUPLICATE KEY UPDATE 가 통과하는지.
--     1970-01-01 은 실제 트래픽과 섞이지 않는 날짜라서 골랐다. 끝에서 지운다.
INSERT INTO site_statistics (visit_date, visit_count) VALUES ('1970-01-01', 1)
    ON DUPLICATE KEY UPDATE visit_count = visit_count + 1;
INSERT INTO site_visit_hourly (visit_date, visit_hour, view_count) VALUES ('1970-01-01', 0, 1)
    ON DUPLICATE KEY UPDATE view_count = view_count + 1;

-- 뷰로 넣은 값이 새 이름 테이블에 실제로 들어갔는지 확인한다. 각각 1행이어야 한다.
SELECT 'md_visit_daily'     AS t, COUNT(*) AS cnt FROM md_visit_daily     WHERE visit_date = '1970-01-01'
UNION ALL
SELECT 'md_pageview_hourly' AS t, COUNT(*) AS cnt FROM md_pageview_hourly WHERE visit_date = '1970-01-01';

-- 검증용 흔적 제거.
DELETE FROM md_visit_daily     WHERE visit_date = '1970-01-01';
DELETE FROM md_pageview_hourly WHERE visit_date = '1970-01-01';


-- ---------------------------------------------------------------------------
-- 뷰 없이 가는 경우
--
-- 위 검증에서 ON DUPLICATE KEY UPDATE 가 뷰에서 막히면, 뷰로 무중단을 기대할 수
-- 없다. 그때는 2단계 뷰를 전부 지우고(3단계 실행) 짧은 점검 시간을 잡아
-- "톰캣 정지 → 1단계 → 새 WAR 배포 → 기동" 순으로 처리한다.
-- ---------------------------------------------------------------------------


-- === 3단계. 새 WAR 가 안정화된 뒤 (며칠 뒤 별도로 실행) ===
--
-- 뷰를 남겨두면 구 이름으로도 계속 쓸 수 있어서, 정리한 의미가 없어지고
-- 다음 사람이 어느 쪽이 진짜인지 다시 헷갈린다. 롤백 가능성이 사라지면 지운다.
--
-- DROP VIEW IF EXISTS
--     md_tests, md_test_questions, md_test_results, md_test_history,
--     egen_teto_test_result,
--     balance_questions, balance_comments, balance_comment_like, balance_vote_log,
--     md_test_category, md_reports, md_users, id_sequence,
--     site_visit_log, site_statistics, site_visit_hourly;


-- === 롤백 (1·2단계를 되돌릴 때) ===
--
-- 뷰를 먼저 지워야 한다. 뷰가 살아 있으면 구 이름이 이미 점유돼 있어서
-- RENAME 이 "Table already exists" 로 실패한다.
--
-- DROP VIEW IF EXISTS
--     md_tests, md_test_questions, md_test_results, md_test_history,
--     egen_teto_test_result,
--     balance_questions, balance_comments, balance_comment_like, balance_vote_log,
--     md_test_category, md_reports, md_users, id_sequence,
--     site_visit_log, site_statistics, site_visit_hourly;
--
-- RENAME TABLE
--     md_test                 TO md_tests,
--     md_test_question        TO md_test_questions,
--     md_test_result_type     TO md_test_results,
--     md_test_play            TO md_test_history,
--     md_egenteto_play        TO egen_teto_test_result,
--     md_balance_game         TO balance_questions,
--     md_balance_comment      TO balance_comments,
--     md_balance_comment_like TO balance_comment_like,
--     md_balance_vote         TO balance_vote_log,
--     md_content_category     TO md_test_category,
--     md_content_report       TO md_reports,
--     md_user                 TO md_users,
--     md_id_sequence          TO id_sequence,
--     md_visit_log            TO site_visit_log,
--     md_visit_daily          TO site_statistics,
--     md_pageview_hourly      TO site_visit_hourly;
