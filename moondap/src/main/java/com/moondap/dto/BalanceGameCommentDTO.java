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
    
    // 작성일
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
