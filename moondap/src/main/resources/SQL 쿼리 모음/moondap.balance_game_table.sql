CREATE TABLE moondap.MdBoard (
  no int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  id varchar(64) DEFAULT NULL UNIQUE,
  title varchar(100) NOT NULL,
  writer varchar(100) NOT NULL,
  content text,
  created_at timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

SELECT * FROM moondap.balance_questions WHERE id = 'B0001-260228';
        
-- DROP TABLE moondap.balance_questions;

CREATE TABLE moondap.balance_questions (
    -- 시스템용 일련번호 (PK)
    no INT AUTO_INCREMENT PRIMARY KEY COMMENT '시스템 관리용 일련번호',
    
    -- 서비스용 고유 ID (Unique Key)
    id VARCHAR(50) NOT NULL UNIQUE COMMENT '질문 고유 ID (예: B0001-260108)',

    -- 기본 정보
    title VARCHAR(255) NOT NULL COMMENT '밸런스 게임 질문 내용',
    is_spicy BOOLEAN DEFAULT FALSE COMMENT '매운맛 질문 여부',
    category VARCHAR(50) NOT NULL COMMENT '카테고리',
    
    -- 선택지 정보
    option_1_text VARCHAR(200) NOT NULL COMMENT '선택지 1 내용',
    option_1_image_path VARCHAR(200) COMMENT '선택지 1 이미지 경로',
    option_2_text VARCHAR(200) NOT NULL COMMENT '선택지 2 내용',
    option_2_image_path VARCHAR(200) COMMENT '선택지 2 이미지 경로',
    
    -- 통계 및 조회수 정보
    total_count INT DEFAULT 0 COMMENT '총 참여자 수',
    option_1_count INT DEFAULT 0 COMMENT '선택지 1 선택자 수',
    option_2_count INT DEFAULT 0 COMMENT '선택지 2 선택자 수',
	share_count INT DEFAULT 0 COMMENT '공유 수',
    
    -- 작성자 정보 및 메타데이터
    user_id VARCHAR(50) NOT NULL COMMENT '작성자',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 댓글 테이블
SELECT * FROM moondap.balance_comments;

CREATE TABLE moondap.balance_comments (
    -- 시스템용 PK
    no INT AUTO_INCREMENT PRIMARY KEY COMMENT '댓글 일련번호',
    
    -- 질문과의 연관관계 (FK)
    question_id VARCHAR(50) NOT NULL COMMENT '연결된 질문 고유 ID',
    
    -- 작성자 정보
    user_id VARCHAR(50) COMMENT '작성자 ID',
    nickname VARCHAR(50) COMMENT '작성 당시 닉네임',
    
    -- 댓글 내용
    content VARCHAR(100) NOT NULL COMMENT '댓글 내용 (최대 100자)',
    
    -- 투표 정보 (UI 표시용 핵심 컬럼)
    selected_side ENUM('left', 'right') NOT NULL COMMENT '선택한 진영 (left/right)',
    
    -- 상태 및 메타데이터
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
    
    -- 인덱스 (조회 성능 최적화)
    INDEX idx_question_id_created (question_id, created_at DESC),
    CONSTRAINT fk_comment_question FOREIGN KEY (question_id) REFERENCES balance_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


        INSERT INTO moondap.balance_comments(question_id, selected_side ,content)
        VALUES ('B0001-260228', 'left', 'test');
        
        
        
        