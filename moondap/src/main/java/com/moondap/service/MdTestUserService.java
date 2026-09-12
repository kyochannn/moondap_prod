package com.moondap.service;

import com.moondap.common.exception.UserMessageException;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.dto.MdContentItemDTO;
import com.moondap.mapper.MdTestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.moondap.config.CacheConfig;

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
            throw new UserMessageException("답변 데이터가 올바르지 않습니다.");
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
                    .orElse(null);

            if (matchedResult == null) {
                // 어떤 결과 구간에도 속하지 않았다. 결과들의 min/max 범위가 0~100 을
                // 다 덮지 못하거나 서로 어긋난 것이므로 설정 오류다.
                // 사용자에게는 첫 번째 결과라도 보여주되, 반드시 추적 가능하게 남긴다.
                logUnmatchedResult(testId, "SCORE", "점수 " + percentage + "% 에 해당하는 구간 없음", results);
                matchedResult = results.isEmpty() ? null : results.get(0);
            }

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
                    .filter(r -> java.util.Objects.equals(r.getResultTitle(), targetDomain))
                    .findFirst()
                    .orElse(null);

            if (matchedResult == null) {
                // 질문의 domain 값과 결과의 result_title 이 정확히 일치해야 매칭되는 구조다.
                // 오타 하나로도 전원이 엉뚱한 결과를 받게 되므로 반드시 추적 가능하게 남긴다.
                logUnmatchedResult(testId, "TYPE", "도메인 '" + targetDomain + "' 과 같은 제목의 결과 없음", results);
                matchedResult = results.isEmpty() ? null : results.get(0);
            }

            if (matchedResult != null) {
                matchedResult.setBreakdown(breakdown);
                matchedResult.setDomainAnalysisMap(analysisMap); // 상세 분석 데이터 저장
            }
            return matchedResult;
        }
    }

    /**
     * 결과 매칭 실패를 기록한다.
     *
     * <p>매칭에 실패해도 첫 번째 결과를 돌려주기 때문에 사용자 화면은 정상으로 보인다.
     * 즉 관리자가 결과 설정을 잘못해도 아무도 알아채지 못한다. 그래서 조용히 넘어가지 않고
     * 어떤 테스트의 어떤 설정이 문제인지 로그로 남긴다.
     */
    private void logUnmatchedResult(Long testId, String testType, String reason,
                                    List<MdTestResultDTO> results) {
        String candidates = results.stream()
                .map(MdTestResultDTO::getResultTitle)
                .map(t -> t == null ? "(제목없음)" : t)
                .collect(java.util.stream.Collectors.joining(", "));

        log.warn("결과 매칭 실패 [testId={}, type={}] {} / 등록된 결과: [{}] "
                        + "→ 첫 번째 결과로 대체함. 테스트 결과 설정을 확인하세요.",
                testId, testType, reason, candidates);
    }

    /**
     * 테스트 참여자 수를 1 증가시킵니다.
     */
    public void incrementPlayCount(Long testId) {
        mdTestMapper.updatePlayCount(testId);
    }

    /**
     * 전체 리스트 페이지용 콘텐츠 리스트를 조회합니다.
     *
     * <p>md_tests 와 balance_questions 를 통째로 UNION 한 뒤 정렬·LIMIT 하는 쿼리라
     * 인덱스를 타지 못한다. 메인 페이지 한 번에 이 조회가 3번 일어나므로 캐시한다.
     * 콘텐츠가 등록·수정·삭제되면 캐시를 비운다(MdTestAdminService, StandardBalanceGameService).
     */
    @Cacheable(cacheNames = CacheConfig.CONTENT_LIST)
    public List<MdContentItemDTO> getAllContentList(String category, String sort, String type, int offset, int limit) {
        return mdTestMapper.selectAllContentList(category, sort, type, offset, limit);
    }

}
