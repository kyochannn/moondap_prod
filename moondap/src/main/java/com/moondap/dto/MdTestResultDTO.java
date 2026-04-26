package com.moondap.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

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
    private Integer calculatedScore;
    private List<ScoreBreakdown> breakdown; // 디버깅용 점수 내역
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class ScoreBreakdown {
        private String questionText;
        private int originalAnswer;
        private int finalScore;
        private boolean isReverse;
    }
}
