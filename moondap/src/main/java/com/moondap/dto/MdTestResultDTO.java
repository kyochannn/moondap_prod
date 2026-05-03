package com.moondap.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private Map<String, DomainAnalysis> domainAnalysisMap; // 추가: 도메인별 상세 분석 데이터
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class DomainAnalysis {
        private double average;
        private int totalScore;
        private int questionCount;
    }

    @Data
    public static class ScoreBreakdown {
        private String questionText;
        private String domain;      // 추가: 해당 질문의 성향 도메인
        private int originalAnswer;
        private int finalScore;
        private boolean isReverse;
    }
}
