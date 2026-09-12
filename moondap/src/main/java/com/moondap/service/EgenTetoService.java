package com.moondap.service;

import com.moondap.common.exception.UserMessageException;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.moondap.config.CacheConfig;
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

    // --- 문항 매핑 상수 (3:3 밸런스 조정 완료) ---
    // 남성(Male) 매핑
    private static final int[] M_STYLE_IDX =    {0, 4, 9, 14, 18, 22};
    private static final int[] M_STYLE_REV =    {0, 9, 18};    // 에겐형 3개 (다양성/탐색)
    
    private static final int[] M_SOCIAL_IDX =   {2, 5, 11, 13, 15, 23};
    private static final int[] M_SOCIAL_REV =   {5, 13, 23};   // 에겐형 3개 (공감/살핌/조화)
    
    private static final int[] M_INNER_IDX =    {1, 6, 10, 16, 17, 19};
    private static final int[] M_INNER_REV =    {6, 16, 19};   // 에겐형 3개 (털어놓음/눈물/안타까워함)
    
    private static final int[] M_AMBITION_IDX = {3, 7, 8, 12, 20, 21};
    private static final int[] M_AMBITION_REV = {7, 12, 21};   // 에겐형 3개 (불안감/부담감/도움요청)

    // 여성(Female) 매핑
    private static final int[] W_STYLE_IDX =    {0, 4, 9, 14, 18, 22};
    private static final int[] W_STYLE_REV =    {0, 9, 18};    // 에겐형 3개 (시도/유행관리/여성스러움)
    
    private static final int[] W_SOCIAL_IDX =   {2, 10, 13, 15, 17, 21};
    private static final int[] W_SOCIAL_REV =   {10, 13, 17};   // 에겐형 3개 (뒷담화/살핌/따뜻함)
    
    private static final int[] W_INNER_IDX =    {1, 5, 8, 11, 20, 23};
    private static final int[] W_INNER_REV =    {1, 5, 23};    // 에겐형 3개 (거리두기/망설임/관계중시)
    
    private static final int[] W_AMBITION_IDX = {3, 6, 7, 12, 16, 19};
    private static final int[] W_AMBITION_REV = {12, 16, 19};  // 에겐형 3개 (관계그림/동요/스트레스)

    private final EgenTetoMapper egenTetoMapper;

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
            throw new UserMessageException("비정상적인 응답 데이터입니다. (답변 개수 부족)");
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
        int score = 2; // 기본값
        if (answer.equals("매우 그렇다")) score = 4;
        else if (answer.equals("그렇다")) score = 3;
        else if (answer.equals("그렇지 않다")) score = 2;
        else if (answer.equals("매우 그렇지 않다")) score = 1;

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
        // 정밀한 백분위 산출 (50점=50%, 100점=1%)
        if (score >= 98) return 1;
        if (score >= 95) return 2;
        if (score >= 92) return 4;
        if (score >= 89) return 7;
        if (score >= 86) return 10;
        if (score >= 83) return 13;
        if (score >= 80) return 16;
        if (score >= 77) return 19;
        if (score >= 74) return 22;
        if (score >= 71) return 25;
        if (score >= 68) return 28;
        if (score >= 65) return 31;
        if (score >= 62) return 34;
        if (score >= 59) return 37;
        if (score >= 56) return 40;
        if (score >= 53) return 44;
        if (score >= 51) return 47;
        return 50;
    }

    /**
     * 특정 유저의 테스트 결과 조회
     */
    public EgenTetoDTO getTestResult(String userNo) {
        return egenTetoMapper.selectTestResult(userNo);
    }

    /**
     * 전체 사용자 점수 통계 조회.
     *
     * <p>이전에는 인스턴스 필드에 직접 캐싱했는데, 싱글톤 빈을 여러 요청 스레드가
     * 동시에 읽고 쓰는데도 동기화나 volatile 이 없었다. 표준 캐시로 옮겨 그 문제를
     * 없앴다. 만료 시간은 CacheConfig 에서 관리한다(30분).
     */
    @Cacheable(cacheNames = CacheConfig.EGEN_STATS, key = "'scoreStatistics'")
    public Map<String, Object> getScoreStatistics() {
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

        return stats;
    }

    /**
     * 성별별 참여 인원수 조회.
     */
    @Cacheable(cacheNames = CacheConfig.EGEN_STATS, key = "'genderCounts'")
    public Map<String, Long> getGenderCounts() {
        Map<String, Object> counts = egenTetoMapper.selectGenderCounts();

        // MyBatis에서 가져온 BigDecimal 또는 Long 처리
        Map<String, Long> result = new HashMap<>();
        result.put("maleCount", ((Number) counts.getOrDefault("maleCount", 0L)).longValue());
        result.put("femaleCount", ((Number) counts.getOrDefault("femaleCount", 0L)).longValue());

        return result;
    }
}
