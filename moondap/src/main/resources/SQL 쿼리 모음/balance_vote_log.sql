-- ============================================================
-- 밸런스 게임 투표 중복 방지 테이블
--
-- ⚠️ 애플리케이션 배포 "전에" 반드시 실행할 것.
--    이 테이블이 없으면 투표 요청이 전부 실패한다.
--
-- 기존 md_visit_log 와 동일하게 UNIQUE 키 + INSERT IGNORE 로
-- 중복을 판정한다. affected rows 가 0 이면 이미 투표한 것이다.
-- ============================================================

CREATE TABLE IF NOT EXISTS moondap.md_balance_vote (
    no          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,

    -- md_balance_game.id (예: BG00001)
    question_id VARCHAR(64)  NOT NULL,

    -- 투표자 식별자.
    --   로그인 사용자 : 'u:' + username
    --   비로그인      : 'ip:' + 클라이언트 IP
    -- 두 형태를 한 컬럼에 담되 접두사로 구분한다.
    -- IPv6(최대 45자) + 접두사를 고려해 넉넉히 잡았다.
    voter_key   VARCHAR(100) NOT NULL,

    voted_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 중복 투표 차단의 핵심. 이 제약이 곧 유일한 진실이다.
    UNIQUE KEY uk_balance_vote (question_id, voter_key),

    -- 게임 삭제 시 관련 로그 정리용
    KEY idx_balance_vote_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ------------------------------------------------------------
-- 참고: 이미 집계된 기존 투표수는 이 로그에 없다.
-- 따라서 테이블 생성 직후에는 기존 투표자도 한 번 더 투표할 수 있다.
-- 과거 투표를 소급 차단할 방법은 없으므로(식별자 기록이 없었다) 그대로 둔다.
-- ------------------------------------------------------------
