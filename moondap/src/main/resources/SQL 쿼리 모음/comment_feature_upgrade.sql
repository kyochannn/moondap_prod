-- ============================================================
-- 댓글 기능 개선 (① 좋아요 서버 판정 / ② 진영 서버 결정 / ③ 익명 본인 삭제)
--
-- ⚠️ 애플리케이션 배포 "전에" 실행할 것.
--
-- 이 스크립트는 여러 번 실행해도 안전하다(멱등).
-- 이미 적용된 항목은 건너뛰고 메시지만 남긴다.
--
-- [멱등성이 필요한 이유]
-- MySQL 의 ALTER TABLE ... ADD COLUMN 에는 IF NOT EXISTS 가 없다.
-- 그래서 두 번 실행하면 "Error Code: 1060. Duplicate column name" 으로 멈추고,
-- 그 뒤에 있는 문장들이 아예 실행되지 않는다. 운영 DB 와 개발 DB 에 나눠 적용하거나
-- 중간에 끊겼을 때 이 차이를 눈치채기 어려워, information_schema 로 확인 후 실행한다.
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
-- 적용 결과 확인 — 3행이 모두 '있음' 이어야 정상
-- ────────────────────────────────────────────────────────────
SELECT 'balance_comment_like 테이블' AS 항목,
       IF(COUNT(*) > 0, '있음', '없음') AS 상태
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'moondap' AND TABLE_NAME = 'balance_comment_like'
UNION ALL
SELECT 'balance_vote_log.selected_side',
       IF(COUNT(*) > 0, '있음', '없음')
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'moondap' AND TABLE_NAME = 'balance_vote_log'
   AND COLUMN_NAME = 'selected_side'
UNION ALL
SELECT 'balance_comments.anon_id',
       IF(COUNT(*) > 0, '있음', '없음')
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'moondap' AND TABLE_NAME = 'balance_comments'
   AND COLUMN_NAME = 'anon_id';
