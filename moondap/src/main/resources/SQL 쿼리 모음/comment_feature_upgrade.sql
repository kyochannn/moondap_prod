-- ============================================================
-- 댓글 기능 개선 (① 좋아요 서버 판정 / ② 진영 서버 결정 / ③ 익명 본인 삭제)
--
-- ⚠️ 애플리케이션 배포 "전에" 실행할 것.
--
-- 이 스크립트는 여러 번 실행해도 안전하다(멱등).
-- 이미 적용된 항목은 건너뛰고 메시지만 남긴다.
--
-- [멱등성을 information_schema 로 구현한 이유]
-- 실제 서버는 MariaDB 10.1.13 이고, MariaDB 는 ALTER TABLE ... ADD COLUMN IF NOT EXISTS
-- 를 지원한다. 그럼에도 그 문법을 쓰지 않는 것은 MySQL 에는 없는 확장이기 때문이다.
-- information_schema 로 확인하는 방식은 양쪽에서 모두 동작한다.
--
-- 멱등성 자체가 필요한 이유는, 한 번 실패하면 그 지점에서 스크립트가 멈추기 때문이다.
-- 실제로 재실행 시 "Error Code: 1060. Duplicate column name" 으로 중단되어
-- 뒤에 있던 anon_id 컬럼과 인덱스 생성이 실행되지 않을 뻔했다.
-- ============================================================


-- ────────────────────────────────────────────────────────────
-- ① 댓글 좋아요 기록
--
-- (comment_no, voter_key) 를 PK 로 두어 중복 좋아요를 DB 가 막는다.
-- 이 기록이 있으면 "내가 누른 댓글"을 서버가 알려줄 수 있어,
-- 기기를 바꿔도 하트 상태가 유지된다.
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS moondap.balance_comment_like (
    comment_no INT          NOT NULL COMMENT 'balance_comments.no',

    -- 투표와 동일한 형식. 로그인: 'u:' + username / 비로그인: 'ip:' + 주소
    voter_key  VARCHAR(100) NOT NULL COMMENT '좋아요 누른 주체',

    liked_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (comment_no, voter_key),

    -- 댓글이 지워지면 좋아요 기록도 함께 정리된다.
    CONSTRAINT fk_comment_like_comment
        FOREIGN KEY (comment_no) REFERENCES moondap.balance_comments(no) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ────────────────────────────────────────────────────────────
-- ② 투표 로그에 선택 진영 기록
--
-- 댓글의 selected_side 를 클라이언트가 보내던 것을 서버가 결정하기 위함이다.
--
-- 기존 행은 side 를 모르므로 NULL 이다. 그런 사용자가 댓글을 쓰면
-- 애플리케이션이 클라이언트 값을 한 번만 받아들이고 이 컬럼을 채운다(자가 복구).
-- ────────────────────────────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'moondap'
       AND TABLE_NAME   = 'balance_vote_log'
       AND COLUMN_NAME  = 'selected_side'
);
SET @stmt := IF(@col_exists = 0,
    'ALTER TABLE moondap.balance_vote_log
        ADD COLUMN selected_side ENUM(''left'',''right'') NULL
        COMMENT ''투표한 진영. 기존 행은 NULL(첫 댓글 작성 시 보정됨)''',
    'SELECT ''[건너뜀] balance_vote_log.selected_side 이미 존재'' AS result'
);
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;


-- ────────────────────────────────────────────────────────────
-- ③ 익명 작성자 식별자
--
-- 서버가 쿠키로 발급한 임의 토큰을 저장한다. 이 토큰을 가진 브라우저만
-- 자기 댓글을 지울 수 있다. 닉네임이 같아도 토큰이 다르므로 사칭과 구분된다.
--
-- 기존 댓글은 NULL 이라 작성자 본인 삭제가 되지 않는다(관리자만 가능).
-- 소급 적용할 방법이 없으므로 그대로 둔다.
-- ────────────────────────────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'moondap'
       AND TABLE_NAME   = 'balance_comments'
       AND COLUMN_NAME  = 'anon_id'
);
SET @stmt := IF(@col_exists = 0,
    'ALTER TABLE moondap.balance_comments
        ADD COLUMN anon_id VARCHAR(36) NULL
        COMMENT ''익명 작성자 식별 토큰(로그인 사용자는 NULL)''',
    'SELECT ''[건너뜀] balance_comments.anon_id 이미 존재'' AS result'
);
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;


SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = 'moondap'
       AND TABLE_NAME   = 'balance_comments'
       AND INDEX_NAME   = 'idx_comment_anon'
);
SET @stmt := IF(@idx_exists = 0,
    'CREATE INDEX idx_comment_anon ON moondap.balance_comments (anon_id)',
    'SELECT ''[건너뜀] idx_comment_anon 이미 존재'' AS result'
);
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;


-- ────────────────────────────────────────────────────────────
-- 적용 결과 확인 — status 가 모두 OK 여야 정상
--
-- 컬럼 별칭에 한글을 쓰지 않는다. MariaDB 10.1 에서는 따옴표 없는 비ASCII
-- 식별자가 접속 문자셋에 따라 파싱 오류(ERROR 1064)를 낸다.
-- ────────────────────────────────────────────────────────────
SELECT 'balance_comment_like (table)' AS item, IF(COUNT(*) > 0, 'OK', 'MISSING') AS status
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'moondap' AND TABLE_NAME = 'balance_comment_like'
UNION ALL
SELECT 'balance_vote_log.selected_side', IF(COUNT(*) > 0, 'OK', 'MISSING')
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'moondap' AND TABLE_NAME = 'balance_vote_log'
   AND COLUMN_NAME = 'selected_side'
UNION ALL
SELECT 'balance_comments.anon_id', IF(COUNT(*) > 0, 'OK', 'MISSING')
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'moondap' AND TABLE_NAME = 'balance_comments'
   AND COLUMN_NAME = 'anon_id'
UNION ALL
SELECT 'idx_comment_anon (index)', IF(COUNT(*) > 0, 'OK', 'MISSING')
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = 'moondap' AND TABLE_NAME = 'balance_comments'
   AND INDEX_NAME = 'idx_comment_anon';
