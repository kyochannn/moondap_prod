-- 테이블 이름 정리 — 3단계. 구 이름 호환 뷰 제거
--
-- table_rename.sql 의 2단계에서 만든 구 이름 뷰 16개를 지운다.
-- table_rename.sql 을 통째로 다시 돌리면 1단계 RENAME 이 다시 실행돼 실패하므로
-- (이미 이름이 바뀌어 있다) 3단계만 따로 떼어 둔다.
--
-- 뷰를 남겨두면 구 이름으로도 계속 읽고 쓸 수 있어서, 이름을 정리한 의미가
-- 없어진다. 다음 사람이 md_balance_game 과 balance_questions 중 어느 쪽이
-- 진짜인지 다시 헷갈리게 된다.
--
-- 적용:  mysql -u <user> -p moondap < table_rename_dropviews.sql
--
-- ---------------------------------------------------------------------------
-- 지우기 전에 알아야 할 것
--
-- 이 뷰는 "구버전 WAR 가 계속 동작하게 하는" 장치다. 지우고 나면 서버의
-- ROOT.war.bak 으로 되돌려도 구버전이 구 이름을 찾지 못해 500 이 난다.
-- 다만 완전히 막히는 건 아니다 — 맨 아래 "롤백이 필요해지면" 블록으로
-- 뷰를 다시 만들면 구버전이 곧바로 살아난다. 그 절차를 알고 있으면 된다.
-- ---------------------------------------------------------------------------


-- === 1. 지워도 되는 상태인지 확인 ===
--
-- 아래 세 쿼리를 먼저 보고, 이상하면 DROP 을 실행하지 않는다.

-- (1) 새 이름 테이블 16개가 BASE TABLE 로 있고, 구 이름 16개가 VIEW 로 있어야 한다.
--     base_tables = 16, compat_views = 16 이 기대값이다.
SELECT SUM(TABLE_TYPE = 'BASE TABLE') AS base_tables,
       SUM(TABLE_TYPE = 'VIEW')       AS compat_views
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'moondap';

-- (2) 새 WAR 가 실제로 새 이름 테이블에 쓰고 있는지 본다.
--     배포 후 방문이 있었다면 오늘 날짜 행이 있어야 한다. 0 이면 아직 트래픽이
--     없었거나(새벽) 애플리케이션이 이 테이블을 못 쓰고 있는 것이다. 후자라면
--     뷰를 지우면 안 된다.
SELECT (SELECT COUNT(*) FROM md_visit_log   WHERE visit_date = CURDATE()) AS visit_log_today,
       (SELECT COUNT(*) FROM md_visit_daily WHERE visit_date = CURDATE()) AS visit_daily_today,
       (SELECT COUNT(*) FROM md_pageview_hourly WHERE visit_date = CURDATE()) AS pageview_today;

-- (3) 본체 테이블에 데이터가 그대로 있는지. RENAME 은 데이터를 옮기지 않으므로
--     당연히 그대로여야 하지만, 지우기 전에 한 번 눈으로 본다.
SELECT 'md_test'            AS t, COUNT(*) AS cnt FROM md_test
UNION ALL SELECT 'md_test_question',        COUNT(*) FROM md_test_question
UNION ALL SELECT 'md_test_result_type',     COUNT(*) FROM md_test_result_type
UNION ALL SELECT 'md_test_play',            COUNT(*) FROM md_test_play
UNION ALL SELECT 'md_egenteto_play',        COUNT(*) FROM md_egenteto_play
UNION ALL SELECT 'md_balance_game',         COUNT(*) FROM md_balance_game
UNION ALL SELECT 'md_balance_comment',      COUNT(*) FROM md_balance_comment
UNION ALL SELECT 'md_balance_comment_like', COUNT(*) FROM md_balance_comment_like
UNION ALL SELECT 'md_balance_vote',         COUNT(*) FROM md_balance_vote
UNION ALL SELECT 'md_content_category',     COUNT(*) FROM md_content_category
UNION ALL SELECT 'md_content_report',       COUNT(*) FROM md_content_report
UNION ALL SELECT 'md_user',                 COUNT(*) FROM md_user
UNION ALL SELECT 'md_id_sequence',          COUNT(*) FROM md_id_sequence
UNION ALL SELECT 'md_visit_log',            COUNT(*) FROM md_visit_log
UNION ALL SELECT 'md_visit_daily',          COUNT(*) FROM md_visit_daily
UNION ALL SELECT 'md_pageview_hourly',      COUNT(*) FROM md_pageview_hourly;


-- === 2. 뷰 제거 ===
--
-- DROP VIEW 는 뷰 정의만 지운다. 데이터가 든 실제 테이블은 건드리지 않는다.
-- (실수로 DROP TABLE 을 쓰지 않도록 주의할 것. 여기서는 VIEW 가 맞다.)

DROP VIEW IF EXISTS
    md_tests,
    md_test_questions,
    md_test_results,
    md_test_history,
    egen_teto_test_result,
    balance_questions,
    balance_comments,
    balance_comment_like,
    balance_vote_log,
    md_test_category,
    md_reports,
    md_users,
    id_sequence,
    site_visit_log,
    site_statistics,
    site_visit_hourly;


-- === 3. 결과 확인 ===
--
-- base_tables = 16, compat_views = 0 이어야 한다.
SELECT SUM(TABLE_TYPE = 'BASE TABLE') AS base_tables,
       SUM(TABLE_TYPE = 'VIEW')       AS compat_views
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'moondap';

-- 여기까지 왔으면 이름 정리는 끝이다. 이제 운영 DB 에 구 이름은 존재하지 않는다.


-- ---------------------------------------------------------------------------
-- 롤백이 필요해지면 (구버전 WAR 로 되돌려야 할 때)
--
-- 순서가 중요하다. 뷰를 먼저 만들고, 그다음 WAR 를 되돌린다. 반대로 하면
-- 구버전이 뜬 직후부터 뷰가 생길 때까지 500 이 난다.
--
--   1) 아래 CREATE VIEW 16개를 실행한다.
--   2) ./scripts/deploy.sh --rollback
--
-- CREATE OR REPLACE VIEW md_tests              AS SELECT * FROM md_test;
-- CREATE OR REPLACE VIEW md_test_questions     AS SELECT * FROM md_test_question;
-- CREATE OR REPLACE VIEW md_test_results       AS SELECT * FROM md_test_result_type;
-- CREATE OR REPLACE VIEW md_test_history       AS SELECT * FROM md_test_play;
-- CREATE OR REPLACE VIEW egen_teto_test_result AS SELECT * FROM md_egenteto_play;
-- CREATE OR REPLACE VIEW balance_questions     AS SELECT * FROM md_balance_game;
-- CREATE OR REPLACE VIEW balance_comments      AS SELECT * FROM md_balance_comment;
-- CREATE OR REPLACE VIEW balance_comment_like  AS SELECT * FROM md_balance_comment_like;
-- CREATE OR REPLACE VIEW balance_vote_log      AS SELECT * FROM md_balance_vote;
-- CREATE OR REPLACE VIEW md_test_category      AS SELECT * FROM md_content_category;
-- CREATE OR REPLACE VIEW md_reports            AS SELECT * FROM md_content_report;
-- CREATE OR REPLACE VIEW md_users              AS SELECT * FROM md_user;
-- CREATE OR REPLACE VIEW id_sequence           AS SELECT * FROM md_id_sequence;
-- CREATE OR REPLACE VIEW site_visit_log        AS SELECT * FROM md_visit_log;
-- CREATE OR REPLACE VIEW site_statistics       AS SELECT * FROM md_visit_daily;
-- CREATE OR REPLACE VIEW site_visit_hourly     AS SELECT * FROM md_pageview_hourly;
--
-- 완전히 되돌리려면(테이블 이름까지) table_rename.sql 맨 아래 롤백 블록을 쓴다.
-- ---------------------------------------------------------------------------
