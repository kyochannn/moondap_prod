package com.moondap.service;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.dto.MdContentItemDTO;
import com.moondap.mapper.MdTestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MdTestUserService {

    private final MdTestMapper mdTestMapper;

    /**
     * testKey를 기반으로 테스트의 모든 정보(기본정보, 질문목록, 결과목록)를 조회합니다.
     */
    public MdTestDTO getFullTestData(String testKey) {
        MdTestDTO test = mdTestMapper.selectTestByTestKey(testKey);
        if (test != null) {
            test.setQuestions(mdTestMapper.selectQuestions(test.getId()));
            test.setResults(mdTestMapper.selectResults(test.getId()));
        }
        return test;
    }

    /**
     * 사용자 답변을 기반으로 최적의 결과 유형을 계산합니다.
     * @param testId 테스트 ID
     * @param answers 질문 순서대로 나열된 점수 리스트 (1~5점)
     * @return 매칭된 MdTestResultDTO
     */
    public MdTestResultDTO calculateResult(Long testId, List<Integer> answers) {
        MdTestDTO test = mdTestMapper.selectTest(testId);
        List<MdTestQuestionDTO> questions = mdTestMapper.selectQuestions(testId);
        List<MdTestResultDTO> results = mdTestMapper.selectResults(testId);

        if (questions.size() != answers.size()) {
            log.error("질문 수({})와 답변 수({})가 일치하지 않습니다.", questions.size(), answers.size());
            throw new IllegalArgumentException("답변 데이터가 올바르지 않습니다.");
        }

        // 1. 점수 합산 (모든 유형 공통)
        Map<String, Double> domainScores = new HashMap<>();
        Map<String, Integer> domainCounts = new HashMap<>();
        List<MdTestResultDTO.ScoreBreakdown> breakdown = new java.util.ArrayList<>();
        int totalScore = 0;

        for (int i = 0; i < questions.size(); i++) {
            MdTestQuestionDTO q = questions.get(i);
            int originalAnswer = answers.get(i);
            int score = originalAnswer;

            // 역채점 처리 (5점 척도 기준)
            boolean reverse = Boolean.TRUE.equals(q.getReverse());
            if (reverse) {
                score = 6 - score;
            }

            // 브레이크다운 기록
            MdTestResultDTO.ScoreBreakdown b = new MdTestResultDTO.ScoreBreakdown();
            b.setQuestionText(q.getQuestionText());
            b.setDomain(q.getDomain()); // 도메인 정보 기록 추가
            b.setOriginalAnswer(originalAnswer);
            b.setFinalScore(score);
            b.setReverse(reverse);
            breakdown.add(b);

            String domain = q.getDomain();
            domainScores.put(domain, domainScores.getOrDefault(domain, 0.0) + score);
            domainCounts.put(domain, domainCounts.getOrDefault(domain, 0) + 1);
            totalScore += score;
        }

        // 2. 테스트 유형에 따른 결과 매칭
        if ("SCORE".equals(test.getTestType())) {
            // [점수 합산형] 총점을 백분율(0-100%)로 환산하여 minScore ~ maxScore 범위에 있는 결과 반환
            int maxPossibleScore = questions.size() * 5;
            double rawPercentage = ((double) totalScore / maxPossibleScore) * 100;
            int percentage = (int) Math.round(rawPercentage);
            
            log.info("SCORE Test [id:{}] - Total: {}, Max: {}, Raw%: {}%, Rounded%: {}%", 
                     testId, totalScore, maxPossibleScore, rawPercentage, percentage);

            MdTestResultDTO matchedResult = results.stream()
                    .filter(r -> r.getMinScore() != null && r.getMaxScore() != null)
                    .filter(r -> percentage >= r.getMinScore() && percentage <= r.getMaxScore())
                    .findFirst()
                    .orElse(results.isEmpty() ? null : results.get(0));

            if (matchedResult != null) {
                matchedResult.setCalculatedScore(percentage);
                matchedResult.setBreakdown(breakdown);
            }
            return matchedResult;
        } else {
            // [유형형] 도메인별 평균 점수가 가장 높은 결과 매칭
            Map<String, MdTestResultDTO.DomainAnalysis> analysisMap = new HashMap<>();
            String bestDomain = null;
            double maxAvg = -1.0;

            for (String domainKey : domainScores.keySet()) {
                double total = domainScores.get(domainKey);
                int count = domainCounts.get(domainKey);
                double avg = total / count;

                MdTestResultDTO.DomainAnalysis analysis = new MdTestResultDTO.DomainAnalysis();
                analysis.setTotalScore((int) total);
                analysis.setQuestionCount(count);
                analysis.setAverage(avg);
                analysisMap.put(domainKey, analysis);

                if (avg > maxAvg) {
                    maxAvg = avg;
                    bestDomain = domainKey;
                }
            }

            final String targetDomain = bestDomain;
            MdTestResultDTO matchedResult = results.stream()
                    .filter(r -> r.getResultTitle().equals(targetDomain))
                    .findFirst()
                    .orElse(results.isEmpty() ? null : results.get(0));

            if (matchedResult != null) {
                matchedResult.setBreakdown(breakdown);
                matchedResult.setDomainAnalysisMap(analysisMap); // 상세 분석 데이터 저장
            }
            return matchedResult;
        }
    }

    /**
     * 테스트 참여자 수를 1 증가시킵니다.
     */
    public void incrementPlayCount(Long testId) {
        mdTestMapper.updatePlayCount(testId);
    }

    /**
     * 메인 페이지용 인기 콘텐츠 리스트를 조회합니다.
     */
    public List<MdContentItemDTO> getPopularContent(int limit) {
        return mdTestMapper.selectPopularContent(limit);
    }

    /**
     * 전체 리스트 페이지용 콘텐츠 리스트를 조회합니다.
     */
    public List<MdContentItemDTO> getAllContentList(String category, String sort, String type, int offset, int limit) {
        return mdTestMapper.selectAllContentList(category, sort, type, offset, limit);
    }
}
