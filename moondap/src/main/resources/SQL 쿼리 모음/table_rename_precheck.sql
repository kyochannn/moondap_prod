-- table_rename.sql 실행 전 점검
--
-- 읽기 전용이다. 아무것도 바꾸지 않는다.
--
-- table_rename.sql 의 대상 목록은 운영 DB 를 직접 보고 만든 것이 아니라
-- 애플리케이션 코드(매퍼 XML · SQL 참조 파일)에서 역산한 것이다. 코드가 모르는
-- 테이블이 운영에 더 있을 수 있고, RENAME 이 코드 밖의 무언가를 깨뜨릴 수도 있다.
-- 그래서 먼저 이걸 돌려 다섯 가지를 확인한다.
--
-- 적용:  mysql -u <user> -p moondap < table_rename_precheck.sql


-- === 1. 서버 버전 ===
--
-- 2단계 호환 뷰로 무중단을 하려면 INSERT ... ON DUPLICATE KEY UPDATE 가 뷰에서
-- 통해야 한다. 이 동작은 버전을 타므로 어떤 서버인지 기록해 둔다.
SELECT VERSION() AS server_version;


-- === 2. 권한 ===
--
-- RENAME 에는 ALTER + DROP + CREATE, 호환 뷰에는 CREATE VIEW 권한이 필요하다.
-- Cafe24 같은 공유 호스팅 계정은 CREATE VIEW 가 빠져 있는 경우가 있다.
-- 없으면 뷰 없이 가는 방식(점검 시간 확보)으로 계획을 바꿔야 한다.
SHOW GRANTS;


-- === 3. rename 대상 대조 ===
--
-- 16개가 전부 'rename 대상' 으로 나와야 한다. 하나라도 빠지면 RENAME TABLE 은
-- 한 문장이라 통째로 실패한다(원자적이므로 절반만 바뀌지는 않는다).
--
-- '코드에 없음' 으로 찍히는 것이 있으면 멈추고 그게 무엇인지 먼저 확인한다.
-- 애플리케이션이 쓰지 않는 백업 테이블일 수도 있고, 코드 역산에서 놓친
-- 진짜 사용 테이블일 수도 있다. 후자면 table_rename.sql 에 추가해야 한다.
SELECT TABLE_NAME,
       TABLE_TYPE,
       CASE WHEN TABLE_NAME IN (
            'md_tests','md_test_questions','md_test_results','md_test_history',
            'md_test_category','md_users','md_reports','egen_teto_test_result',
            'balance_questions','balance_comments','balance_comment_like','balance_vote_log',
            'site_statistics','site_visit_log','site_visit_hourly','id_sequence'
       ) THEN 'rename 대상' ELSE '>>> 코드에 없음 <<<' END AS note
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'moondap'
 ORDER BY note, TABLE_NAME;

-- 위 결과의 요약. expected_16 이 16 이어야 한다.
SELECT SUM(TABLE_NAME IN (
            'md_tests','md_test_questions','md_test_results','md_test_history',
            'md_test_category','md_users','md_reports','egen_teto_test_result',
            'balance_questions','balance_comments','balance_comment_like','balance_vote_log',
            'site_statistics','site_visit_log','site_visit_hourly','id_sequence'
       )) AS expected_16,
       COUNT(*) AS total_objects
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'moondap';


-- === 4. 코드 밖에서 구 이름을 참조하는 것 ===
--
-- RENAME TABLE 은 외래키는 따라오게 고쳐주지만, 뷰·트리거·저장 프로시저·이벤트가
-- 참조하는 테이블 이름은 고쳐주지 않는다. 그런 게 있으면 RENAME 직후 조용히
-- 깨진다(뷰는 조회할 때, 트리거는 INSERT 할 때 터진다).
--
-- 네 결과가 모두 비어 있으면 그냥 진행해도 된다.
SELECT 'VIEW' AS kind, TABLE_NAME AS name, NULL AS extra
  FROM information_schema.VIEWS      WHERE TABLE_SCHEMA   = 'moondap'
UNION ALL
SELECT 'TRIGGER', TRIGGER_NAME, EVENT_OBJECT_TABLE
  FROM information_schema.TRIGGERS   WHERE TRIGGER_SCHEMA = 'moondap'
UNION ALL
SELECT 'ROUTINE', ROUTINE_NAME, ROUTINE_TYPE
  FROM information_schema.ROUTINES   WHERE ROUTINE_SCHEMA = 'moondap'
UNION ALL
SELECT 'EVENT',   EVENT_NAME,   STATUS
  FROM information_schema.EVENTS     WHERE EVENT_SCHEMA   = 'moondap';


-- === 5. 외래키 ===
--
-- RENAME 이 알아서 새 이름을 가리키게 고쳐주지만, 실행 후 실제로 그렇게 됐는지
-- 대조할 수 있도록 지금 상태를 찍어 둔다.
SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME,
       REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
  FROM information_schema.KEY_COLUMN_USAGE
 WHERE TABLE_SCHEMA = 'moondap' AND REFERENCED_TABLE_NAME IS NOT NULL
 ORDER BY TABLE_NAME, CONSTRAINT_NAME;
