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
     * @param answers 질문 순서대로 나열된 점수 리스트 (1~4점)
     * @return 매칭된 MdTestResultDTO
     */
    public MdTestResultDTO calculateResult(Long testId, List<Integer> answers) {
        List<MdTestQuestionDTO> questions = mdTestMapper.selectQuestions(testId);
        List<MdTestResultDTO> results = mdTestMapper.selectResults(testId);

        if (questions.size() != answers.size()) {
            log.error("질문 수({})와 답변 수({})가 일치하지 않습니다.", questions.size(), answers.size());
            throw new IllegalArgumentException("답변 데이터가 올바르지 않습니다.");
        }

        // 도메인별 점수 합산 및 문항 수 카운트
        Map<String, Double> domainScores = new HashMap<>();
        Map<String, Integer> domainCounts = new HashMap<>();

        for (int i = 0; i < questions.size(); i++) {
            MdTestQuestionDTO q = questions.get(i);
            int score = answers.get(i);
            String domain = q.getDomain();

            // 역채점 처리
            if (Boolean.TRUE.equals(q.getReverse())) {
                score = 5 - score;
            }

            domainScores.put(domain, domainScores.getOrDefault(domain, 0.0) + score);
            domainCounts.put(domain, domainCounts.getOrDefault(domain, 0) + 1);
        }

        // 도메인별 평균 점수 계산 및 최고 점수 도메인 찾기
        String bestDomain = null;
        double maxAvg = -1.0;

        for (String domain : domainScores.keySet()) {
            double avg = domainScores.get(domain) / domainCounts.get(domain);
            if (avg > maxAvg) {
                maxAvg = avg;
                bestDomain = domain;
            }
        }

        // 매칭되는 결과 객체 반환
        final String targetDomain = bestDomain;
        return results.stream()
                .filter(r -> r.getResultTitle().equals(targetDomain))
                .findFirst()
                .orElse(results.isEmpty() ? null : results.get(0)); // 매칭 안 되면 첫 번째 결과 반환
    }
}
