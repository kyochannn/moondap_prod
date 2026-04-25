package com.moondap.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MdTestResultDTO {
    private Long id;
    private Long testId;
    private String resultTitle;
    private String resultContent;
    private String resultImage;
    private Double minScore;     // 최소 평균 점수 (1.0~5.0)
    private Double maxScore;     // 최대 평균 점수 (1.0~5.0)
    private Boolean hasNewImage; // 새 이미지 업로드 여부 [NEW]
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
