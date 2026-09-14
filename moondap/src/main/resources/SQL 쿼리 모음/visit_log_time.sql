-- 접속 기록에 시각 추가
--
-- md_visit_log 는 PK 가 (visit_date, ip_address) 라 날짜만 있고 시각이 없었다.
-- 관리자 화면에서 "언제 들어왔는지"를 보려면 시각이 필요하다.
--
-- 적용:  mysql -u <user> -p moondap < visit_log_time.sql
--
-- PK 는 그대로 둔다. 하루 한 IP 당 한 행이라는 제약이 곧 순 방문자 수의 근거이고,
-- 이걸 풀면 지난 기록의 의미가 달라진다. 그래서 이 컬럼에 담기는 값은
-- "그날 그 IP 가 처음 들어온 시각"이다(INSERT IGNORE 라 두 번째부터는 갱신되지 않는다).
--
-- 이미 쌓인 행에는 시각이 없으므로 NULL 로 남는다. 화면에서는 '-' 로 표시한다.
-- 지난 값을 임의로 채우면 없는 사실을 만들어내는 것이라 그대로 둔다.

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'md_visit_log'
      AND COLUMN_NAME = 'first_seen_at') = 0,
  'ALTER TABLE md_visit_log ADD COLUMN first_seen_at DATETIME DEFAULT NULL COMMENT ''그날 처음 접속한 시각''',
  'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
