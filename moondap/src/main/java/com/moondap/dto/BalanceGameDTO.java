package com.moondap.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 
 * 밸런스 게임 정보
 */
@Data
@Getter
@Setter
public class BalanceGameDTO {

    // 시스템 관리용 일련번호 (Auto Increment)
    private Integer no;

    // 서비스용 고유 ID (예: BG000001)
    private String id;

    // 기본 정보
    private String title;
    private Boolean isSpicy;
    private String category;

    // 선택지 정보
    private String option1Text;
    private String option1ImagePath;
    private String option2Text;
    private String option2ImagePath;

    // 통계 및 조회수 정보
    private Integer totalCount;
    private Integer option1Count;
    private Integer option2Count;

    // 작성자 정보 및 메타데이터
    private String userId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    // 해당 데이터부터는 소스단에서 계산하여 넣는 값
    private Integer option1Percent;
    private Integer option2Percent;
    
    private Integer commentCnt;
    
    private String errViewMsg;
    
}