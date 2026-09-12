package com.moondap.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * 밸런스 게임 댓글 new
 */
@Data
@Getter
@Setter
public class BalanceGameCommentDTO {
	
	// 시스템 관리용 일련번호 (Auto Increment)
	private Integer no;
	
	// 연결된 질문 고유 ID
    private String questionId;
    
    // 작성자 ID
    private String userId;
    
    // 작성 당시 닉네임
    private String nickname;
    
    // 댓글 내용
    private String content;
    
    // 댓글 좋아요 수
    private Integer likeCount;
    
    // 선택한 진영 ('left' 또는 'right')
    private String selectedSide;    
    
    // 익명 작성자 식별 토큰. 로그인 사용자는 null.
    // 응답 JSON 에는 내보내지 않는다(다른 사람의 토큰을 알면 그 댓글을 지울 수 있다).
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String anonId;

    // 작성일
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // ── 아래는 DB 컬럼이 아니라 요청자 기준으로 서버가 계산해 내려주는 값 ──

    /**
     * 이 요청자가 좋아요를 눌렀는지 여부.
     *
     * 예전에는 화면이 localStorage 로 판단해서, 기기를 바꾸면 하트가 전부 풀렸다.
     */
    private boolean likedByMe;

    /** 이 요청자가 삭제할 수 있는 댓글인지 여부 (관리자 · 작성자 본인) */
    private boolean deletable;
}
