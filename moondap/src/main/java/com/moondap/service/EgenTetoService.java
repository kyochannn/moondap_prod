package com.moondap.service;

import org.springframework.stereotype.Service;
import com.moondap.dto.EgenTetoDTO;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.moondap.mapper.EgenTetoMapper;

/**
 * 에겐/테토 테스트 점수 계산 및 유형 판별 서비스
 */
@Service
public class EgenTetoService {

    // --- 문항 매핑 상수 ---
    // 남성(M) 매핑
    private static final int[] M_STYLE_IDX =    {0, 4, 9, 14, 18, 22};
    private static final int[] M_STYLE_REV =    {0, 9, 18}; // 에겐형 문항(다양성/탐색)
    private static final int[] M_SOCIAL_IDX =   {2, 5, 11, 13, 21, 23};
    private static final int[] M_SOCIAL_REV =   {5, 13, 21, 23}; // 에겐형 문항(공감/조화)
    private static final int[] M_INNER_IDX =    {1, 6, 7, 16, 17, 19};
    private static final int[] M_INNER_REV =    {6, 7, 16, 19}; // 에겐형 문항(감정공유/눈물)
    private static final int[] M_AMBITION_IDX = {3, 8, 10, 12, 15, 20};
    private static final int[] M_AMBITION_REV = {12}; // 에겐형 문항(부담감)

    // 여성(W) 매핑
    private static final int[] W_STYLE_IDX =    {0, 4, 9, 14, 18, 22};
    private static final int[] W_STYLE_REV =    {0, 9, 18}; // 에겐형 문항(시도/관리/소녀스러움)
    private static final int[] W_SOCIAL_IDX =   {2, 10, 13, 15, 17, 23};
    private static final int[] W_SOCIAL_REV =   {10, 13, 17, 23}; // 에겐형 문항(간접표현/분위기/케어)
    private static final int[] W_INNER_IDX =    {1, 5, 6, 7, 16, 19};
    private static final int[] W_INNER_REV =    {1, 5, 6, 7, 16, 19}; // 에겐형 문항(거리두기/결정장애/동요/스트레스)
    private static final int[] W_AMBITION_IDX = {3, 8, 11, 12, 20, 21};
    private static final int[] W_AMBITION_REV = {12, 21}; // 에겐형 문항(관계지향)

    private final EgenTetoMapper egenTetoMapper;

    // 통계 캐싱을 위한 필드
    private Map<String, Object> cachedStats = null;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30분 (밀리초)

    public EgenTetoService(EgenTetoMapper egenTetoMapper) {
        this.egenTetoMapper = egenTetoMapper;
    }

    /**
     * 질문 응답 데이터(JSON 문자열)를 분석하여 결과 DTO 생성 및 DB 저장
     */
    public EgenTetoDTO calculateResult(String gender, String answersJson) throws Exception {
        
        // JSON 배열 문자열(["A", "B"])에서 문자열 리스트로 수동 변환
        String cleanJson = (answersJson == null) ? "" : answersJson.replace("[", "").replace("]", "").replace("\"", "");
        List<String> answers = Arrays.stream(cleanJson.split(","))
                                     .map(String::trim)
                                     .filter(s -> !s.isEmpty())
                                     .collect(Collectors.toList());
        
        // 데이터 정합성 체크: 질문이 24개인지 확인
        if (answers.size() != 24) {
            throw new IllegalArgumentException("비정상적인 응답 데이터입니다. (답변 개수 부족)");
        }

        EgenTetoDTO result = new EgenTetoDTO();
        result.setUserNo(java.util.UUID.randomUUID().toString());
        result.setGender(gender);
        result.setIsTesterMyself("1"); // 기본값 1 (추후 UI에서 변경 가능하도록 확장 가능)

        // 성별에 따른 문항 매핑 정의
        boolean isMale = "M".equalsIgnoreCase(gender);
        
        // 영역별 점수 합산 변수
        int styleScore, socialScore, innerScore, ambitionScore;
        int styleCount, socialCount, innerCount, ambitionCount;

        if (isMale) {
            styleScore = sumScores(answers, M_STYLE_IDX, M_STYLE_REV);
            socialScore = sumScores(answers, M_SOCIAL_IDX, M_SOCIAL_REV);
            innerScore = sumScores(answers, M_INNER_IDX, M_INNER_REV);
            ambitionScore = sumScores(answers, M_AMBITION_IDX, M_AMBITION_REV);
            styleCount = M_STYLE_IDX.length;
            socialCount = M_SOCIAL_IDX.length;
            innerCount = M_INNER_IDX.length;
            ambitionCount = M_AMBITION_IDX.length;
        } else {
            styleScore = sumScores(answers, W_STYLE_IDX, W_STYLE_REV);
            socialScore = sumScores(answers, W_SOCIAL_IDX, W_SOCIAL_REV);
            innerScore = sumScores(answers, W_INNER_IDX, W_INNER_REV);
            ambitionScore = sumScores(answers, W_AMBITION_IDX, W_AMBITION_REV);
            styleCount = W_STYLE_IDX.length;
            socialCount = W_SOCIAL_IDX.length;
            innerCount = W_INNER_IDX.length;
            ambitionCount = W_AMBITION_IDX.length;
        }

        // 영역별 백분율 환산 (0~100)
        // 각 포인트는 '테토(Teto)' 성향의 강도를 나타냄
        result.setStyleSelfcarePoint(calculatePercent(styleScore, styleCount)); 
        result.setSocialSkillPoint(calculatePercent(socialScore, socialCount));
        result.setInnerTendencyPoint(calculatePercent(innerScore, innerCount));
        result.setAmbitionPoint(calculatePercent(ambitionScore, ambitionCount));

        // 종합 테토 점수 계산 (영역별 점수 평균)
        double totalAvg = (result.getStyleSelfcarePoint() + result.getSocialSkillPoint() + 
                          result.getInnerTendencyPoint() + result.getAmbitionPoint()) / 4.0;
        
        result.setTetoScore((int) Math.round(totalAvg));
        result.setEgenScore(100 - result.getTetoScore());
        
        // 정밀 순위 계산: 테토와 에겐 중 더 강한 성향의 점수를 기준으로 순위 산출
        int maxTendencyScore = Math.max(result.getTetoScore(), result.getEgenScore());
        result.setTopPercent(calculateRank(maxTendencyScore));

        // 최종 유형 판정
        boolean isTeto = result.getTetoScore() >= 50;
        String type = (isTeto ? "테토" : "에겐") + (isMale ? "남" : "녀");
        result.setTestResultType(type);
        
        // 영역별 유형 텍스트 업데이트 (영역 명칭 포함)
        result.setStyleSelfcareResultType(result.getStyleSelfcarePoint() >= 50 ? "스타일 테토형" : "스타일 에겐형");
        result.setSocialSkillResultType(result.getSocialSkillPoint() >= 50 ? "사회적 테토형" : "사회적 에겐형");
        result.setInnerTendencyResultType(result.getInnerTendencyPoint() >= 50 ? "내면 테토형" : "내면 에겐형");
        result.setAmbitionResultType(result.getAmbitionPoint() >= 50 ? "야망 테토형" : "야망 에겐형");

        // DB 저장
        egenTetoMapper.insertTestResult(result);

        return result;
    }

    private int sumScores(List<String> answers, int[] indices, int[] reverseIndices) {
        int sum = 0;
        List<Integer> reverseList = Arrays.stream(reverseIndices).boxed().collect(Collectors.toList());
        
        for (int idx : indices) {
            if (idx < answers.size()) {
                String answer = answers.get(idx);
                boolean isReverse = reverseList.contains(idx);
                sum += convertAnswerToScore(answer, isReverse);
            }
        }
        return sum;
    }

    private int convertAnswerToScore(String answer, boolean isReverse) {
        int score = 2; // 기본값 (그렇지 않다 수준)
        if (answer.contains("매우 그렇다")) score = 4;
        else if (answer.contains("그렇다")) score = 3;
        else if (answer.contains("그렇지 않다")) score = 2;
        else if (answer.contains("매우 그렇지 않다")) score = 1;

        if (isReverse) {
            // 역채점: 그렇다(4) -> 에겐(4)/테토(1), 그렇지 않다(1) -> 에겐(1)/테토(4)
            // 반환값은 항상 '테토' 점수 기준임
            return 5 - score;
        }
        return score;
    }

    private int calculatePercent(int score, int questionCount) {
        if (questionCount == 0) return 0;
        // 4점 척도(1~4)에서 0~100%로 정밀 변환
        // 공식: ((현재점수 - 최소점수) / (최대점수 - 최소점수)) * 100
        // 최소점수 = questionCount * 1, 최대점수 = questionCount * 4
        double percent = ((score - questionCount) / (double)(questionCount * 3)) * 100;
        return (int) Math.round(percent);
    }

    private int calculateRank(int score) {
        if (score >= 95) return 1;
        if (score >= 90) return 3;
        if (score >= 85) return 6;
        if (score >= 80) return 10;
        if (score >= 75) return 15;
        if (score >= 70) return 22;
        if (score >= 65) return 30;
        if (score >= 60) return 40;
        if (score >= 55) return 48;
        return 50;
    }

    /**
     * 특정 유저의 테스트 결과 조회
     */
    public EgenTetoDTO getTestResult(String userNo) {
        return egenTetoMapper.selectTestResult(userNo);
    }

    /**
     * 전체 사용자 점수 통계 조회 (캐싱 적용)
     */
    public Map<String, Object> getScoreStatistics() {
        long now = System.currentTimeMillis();
        
        // 캐시 확인 (30분 이내)
        if (cachedStats != null && (now - lastCacheTime) < CACHE_DURATION) {
            return cachedStats;
        }

        // DB에서 최신 통계 조회
        Map<String, Object> stats = egenTetoMapper.selectScoreStatistics();
        if (stats == null) stats = new HashMap<>();

        long totalCount = ((Number) stats.getOrDefault("totalCount", 0)).longValue();
        
        // 데이터가 부족할 경우(10명 미만) 기본값 사용
        if (totalCount < 10) {
            stats.put("avgScore", 50.0);
            stats.put("stdDevScore", 15.0);
            stats.put("isDefault", true); // 기본값 사용 여부 표시
        } else {
            stats.put("isDefault", false);
        }

        // 캐시 업데이트
        this.cachedStats = stats;
        this.lastCacheTime = now;

        return stats;
    }

    /**
     * 성별별 참여 인원수 조회 (30분 캐시 적용)
     */
    private Map<String, Long> cachedGenderCounts;
    private long lastGenderCacheTime = 0;

    public Map<String, Long> getGenderCounts() {
        long now = System.currentTimeMillis();
        if (cachedGenderCounts == null || (now - lastGenderCacheTime) > 30 * 60 * 1000) {
            Map<String, Object> counts = egenTetoMapper.selectGenderCounts();
            
            // MyBatis에서 가져온 BigDecimal 또는 Long 처리
            Map<String, Long> result = new HashMap<>();
            result.put("maleCount", ((Number) counts.getOrDefault("maleCount", 0L)).longValue());
            result.put("femaleCount", ((Number) counts.getOrDefault("femaleCount", 0L)).longValue());
            
            cachedGenderCounts = result;
            lastGenderCacheTime = now;
        }
        return cachedGenderCounts;
    }
}
