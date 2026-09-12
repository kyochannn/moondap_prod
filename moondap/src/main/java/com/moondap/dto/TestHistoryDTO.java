package com.moondap.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 사용자가 마친 테스트 한 건.
 *
 * <p>제목·이미지는 테스트 테이블을 조인하지 않고 그 시점 값을 복사해 둔다.
 * 제작자가 결과를 수정하거나 테스트를 지워도 보관함에 남은 기록이 빈칸이 되거나
 * 다른 내용으로 바뀌면 안 되기 때문이다.
 */
@Data
public class TestHistoryDTO {

    private Long no;

    private String testKey;
    private Long testId;
    private String testTitle;

    private Long resultId;
    private String resultCode;
    private String resultTitle;
    private String resultImage;
    private Integer score;

    private String userId;
    private String anonId;

    private LocalDateTime createdAt;
}
