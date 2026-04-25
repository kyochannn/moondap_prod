package com.moondap.service;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
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
        int totalScore = 0;

        for (int i = 0; i < questions.size(); i++) {
            MdTestQuestionDTO q = questions.get(i);
            int score = answers.get(i);

            // 역채점 처리 (5점 척도 기준)
            if (Boolean.TRUE.equals(q.getReverse())) {
                score = 6 - score;
            }

            String domain = q.getDomain();
            domainScores.put(domain, domainScores.getOrDefault(domain, 0.0) + score);
            domainCounts.put(domain, domainCounts.getOrDefault(domain, 0) + 1);
            totalScore += score;
        }

        // 2. 테스트 유형에 따른 결과 매칭
        if ("SCORE".equals(test.getTestType())) {
            // [점수 합산형] 총점을 백분율(0-100%)로 환산하여 minScore ~ maxScore 범위에 있는 결과 반환
            // 공식: (실제 총점 / (질문 수 * 5점)) * 100 -> 반올림 처리
            int maxPossibleScore = questions.size() * 5;
            double rawPercentage = ((double) totalScore / maxPossibleScore) * 100;
            int percentage = (int) Math.round(rawPercentage);
            
            log.info("SCORE Test [id:{}] - Total: {}, Max: {}, Raw%: {}%, Rounded%: {}%", 
                     testId, totalScore, maxPossibleScore, rawPercentage, percentage);

            return results.stream()
                    .filter(r -> r.getMinScore() != null && r.getMaxScore() != null)
                    .filter(r -> percentage >= r.getMinScore() && percentage <= r.getMaxScore())
                    .findFirst()
                    .orElse(results.isEmpty() ? null : results.get(0));
        } else {
            // [유형형] 도메인별 평균 점수가 가장 높은 결과 매칭 (기존 로직)
            String bestDomain = null;
            double maxAvg = -1.0;

            for (String domain : domainScores.keySet()) {
                double avg = domainScores.get(domain) / domainCounts.get(domain);
                if (avg > maxAvg) {
                    maxAvg = avg;
                    bestDomain = domain;
                }
            }

            final String targetDomain = bestDomain;
            return results.stream()
                    .filter(r -> r.getResultTitle().equals(targetDomain))
                    .findFirst()
                    .orElse(results.isEmpty() ? null : results.get(0));
        }
    }

    /**
     * 테스트 참여자 수를 1 증가시킵니다.
     */
    public void incrementPlayCount(Long testId) {
        mdTestMapper.updatePlayCount(testId);
    }
}
