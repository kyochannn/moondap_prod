package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.mapper.MdTestMapper;

/**
 * 심리테스트 결과 매칭 테스트.
 *
 * 매칭에 실패해도 첫 번째 결과를 돌려주기 때문에 화면은 정상으로 보인다.
 * 그 fallback 동작 자체는 유지하되(사용자에게 빈 화면을 주는 것보다 낫다),
 * 정상 매칭이 제대로 되는지를 고정해 둔다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestResultMatchingTest {

    private static final long TEST_ID = 1L;

    @Mock
    private MdTestMapper mdTestMapper;

    @InjectMocks
    private MdTestUserService service;

    private MdTestQuestionDTO question(String domain, boolean reverse) {
        MdTestQuestionDTO q = new MdTestQuestionDTO();
        q.setQuestionText("질문");
        q.setDomain(domain);
        q.setReverse(reverse);
        return q;
    }

    private MdTestResultDTO result(String title, Double min, Double max) {
        MdTestResultDTO r = new MdTestResultDTO();
        r.setId(TEST_ID);
        r.setResultTitle(title);
        r.setMinScore(min);
        r.setMaxScore(max);
        return r;
    }

    private void setup(String testType, List<MdTestQuestionDTO> questions, List<MdTestResultDTO> results) {
        MdTestDTO test = new MdTestDTO();
        test.setId(TEST_ID);
        test.setTestType(testType);
        when(mdTestMapper.selectTest(anyLong())).thenReturn(test);
        when(mdTestMapper.selectQuestions(anyLong())).thenReturn(questions);
        when(mdTestMapper.selectResults(anyLong())).thenReturn(results);
    }

    // ── TYPE 형 ────────────────────────────────────────────────

    @Test
    @DisplayName("도메인 평균이 가장 높은 결과를 고른다")
    void picksHighestDomain() {
        setup("TYPE",
                List.of(question("모험형", false), question("안정형", false)),
                List.of(result("모험형", null, null), result("안정형", null, null)));

        MdTestResultDTO matched = service.calculateResult(TEST_ID, List.of(5, 1));

        assertThat(matched.getResultTitle()).isEqualTo("모험형");
    }

    @Test
    @DisplayName("역채점 문항은 점수를 뒤집어 계산한다")
    void appliesReverseScoring() {
        setup("TYPE",
                List.of(question("모험형", true), question("안정형", false)),
                List.of(result("모험형", null, null), result("안정형", null, null)));

        // 모험형 문항에 5점 → 역채점으로 1점, 안정형 3점 → 안정형이 이긴다
        MdTestResultDTO matched = service.calculateResult(TEST_ID, List.of(5, 3));

        assertThat(matched.getResultTitle()).isEqualTo("안정형");
    }

    @Test
    @DisplayName("도메인과 결과 제목이 어긋나면 첫 결과로 대체한다")
    void fallsBackWhenTitleMismatched() {
        // 질문의 domain 은 '모험형' 인데 결과 제목은 '모험가' — 오타 한 글자로 매칭이 깨진다.
        setup("TYPE",
                List.of(question("모험형", false)),
                List.of(result("모험가", null, null), result("안정형", null, null)));

        MdTestResultDTO matched = service.calculateResult(TEST_ID, List.of(5));

        // 화면이 비지 않도록 첫 결과를 주되, 이 상황은 로그(logUnmatchedResult)로 추적된다.
        assertThat(matched.getResultTitle()).isEqualTo("모험가");
    }

    // ── SCORE 형 ───────────────────────────────────────────────

    @Test
    @DisplayName("총점 백분율이 속한 구간의 결과를 고른다")
    void picksMatchingScoreRange() {
        setup("SCORE",
                List.of(question("A", false), question("A", false)),
                List.of(result("낮음", 0.0, 49.0), result("높음", 50.0, 100.0)));

        // 5+5=10점 / 최대 10점 = 100%
        MdTestResultDTO matched = service.calculateResult(TEST_ID, List.of(5, 5));

        assertThat(matched.getResultTitle()).isEqualTo("높음");
        assertThat(matched.getCalculatedScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("어떤 구간에도 속하지 않으면 첫 결과로 대체한다")
    void fallsBackWhenNoRangeMatches() {
        // 구간이 0~100 을 다 덮지 못한다(50~59 가 비어 있음).
        setup("SCORE",
                List.of(question("A", false), question("A", false)),
                List.of(result("낮음", 0.0, 49.0), result("높음", 60.0, 100.0)));

        // 3+3=6 / 10 = 60%... 은 매칭되므로 55% 가 되도록 조정: 5+1=6? → 60%
        // 2+3=5 / 10 = 50% → 어느 구간에도 없음
        MdTestResultDTO matched = service.calculateResult(TEST_ID, List.of(2, 3));

        assertThat(matched.getResultTitle()).isEqualTo("낮음");
        assertThat(matched.getCalculatedScore()).isEqualTo(50);
    }

    // ── 입력 검증 ──────────────────────────────────────────────

    @Test
    @DisplayName("질문 수와 답변 수가 다르면 거부한다")
    void rejectsAnswerCountMismatch() {
        setup("TYPE", List.of(question("A", false), question("B", false)), List.of(result("A", null, null)));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.calculateResult(TEST_ID, List.of(5)))
                .isInstanceOf(com.moondap.common.exception.UserMessageException.class);
    }
}
