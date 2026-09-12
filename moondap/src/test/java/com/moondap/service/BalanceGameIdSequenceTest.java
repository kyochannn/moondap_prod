package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.moondap.common.FileService;
import com.moondap.dto.request.BalanceGameForm;
import com.moondap.mapper.BalanceGameMapper;

/**
 * 밸런스 게임 ID 채번 테스트.
 *
 * 이전 구현의 실패 모드를 그대로 재현해 두었다.
 * 운영 데이터가 실제로 그 상태였다(전체 MAX=BG00010(draft), active MAX=BG00009).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceGameIdSequenceTest {

    @Mock
    private BalanceGameMapper balanceGameMapper;

    @Mock
    private FileService fileService;

    @Mock
    private StatService statService;

    @InjectMocks
    private StandardBalanceGameService service;

    /** 시퀀스가 주어진 값을 돌려주도록 흉내 낸다 */
    private void sequenceReturns(long value) {
        doAnswer(inv -> {
            Map<String, Object> param = inv.getArgument(0);
            param.put("value", value);
            return null;
        }).when(balanceGameMapper).nextBalanceGameSequence(anyMap());
    }

    private String callNextId() {
        return (String) ReflectionTestUtils.invokeMethod(service, "nextBalanceGameId");
    }

    // ── ID 형식 ────────────────────────────────────────────────

    @Test
    @DisplayName("5자리 zero padding 형식을 유지한다")
    void formatsWithPadding() {
        assertThat(StandardBalanceGameService.formatId(1)).isEqualTo("BG00001");
        assertThat(StandardBalanceGameService.formatId(11)).isEqualTo("BG00011");
        assertThat(StandardBalanceGameService.formatId(99999)).isEqualTo("BG99999");
    }

    @Test
    @DisplayName("자릿수가 같아야 문자열 정렬과 숫자 정렬이 일치한다")
    void lexicalOrderMatchesNumericOrder() {
        // 이전/다음 게임 조회가 ORDER BY id (문자열) 로 순서를 정하므로 중요한 성질이다.
        assertThat(StandardBalanceGameService.formatId(9))
                .isLessThan(StandardBalanceGameService.formatId(10));
        assertThat(StandardBalanceGameService.formatId(99))
                .isLessThan(StandardBalanceGameService.formatId(100));
    }

    // ── 채번 동작 ──────────────────────────────────────────────

    @Test
    @DisplayName("시퀀스가 돌려준 번호로 ID 를 만든다")
    void usesSequenceValue() {
        sequenceReturns(11L);

        assertThat(callNextId()).isEqualTo("BG00011");
    }

    @Test
    @DisplayName("연속 호출 시 번호가 겹치지 않는다")
    void consecutiveCallsDoNotCollide() {
        // 시퀀스 테이블의 행 락이 보장하는 성질을 호출 측면에서 확인한다.
        long[] counter = { 10L };
        doAnswer(inv -> {
            Map<String, Object> param = inv.getArgument(0);
            param.put("value", ++counter[0]);
            return null;
        }).when(balanceGameMapper).nextBalanceGameSequence(anyMap());

        assertThat(callNextId()).isEqualTo("BG00011");
        assertThat(callNextId()).isEqualTo("BG00012");
        assertThat(callNextId()).isEqualTo("BG00013");
    }

    @Test
    @DisplayName("채번은 status 필터가 걸린 MAX 조회를 쓰지 않는다")
    void doesNotUseStatusFilteredMaxQuery() throws Exception {
        sequenceReturns(11L);

        callNextId();

        // 이 조회는 active 만 보기 때문에 draft 가 최대값이면 중복 ID 를 발급했다.
        // 채번 경로에서 완전히 빠졌는지 확인한다.
        verify(balanceGameMapper, never()).selectMaxBalanceGameId(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("시퀀스가 초기화되지 않으면 조용히 1번을 쓰지 않고 실패한다")
    void failsLoudlyWhenSequenceMissing() {
        // id_sequence 에 행이 없으면 UPDATE 가 0건이라 값이 비어 온다.
        doAnswer(inv -> null).when(balanceGameMapper).nextBalanceGameSequence(anyMap());

        assertThatThrownBy(this::callNextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id_sequence.sql");
    }

    // ── 이전 구현의 버그 재현 ──────────────────────────────────

    @Test
    @DisplayName("[회귀] draft 가 최대값이어도 기존 ID 를 재발급하지 않는다")
    void doesNotReissueExistingIdWhenDraftIsMax() {
        // 운영 데이터 재현: 전체 MAX=BG00010(draft), active MAX=BG00009
        // 이전 구현은 active MAX(BG00009) + 1 = BG00010 을 발급해 UNIQUE 위반을 냈다.
        // 시퀀스는 전체 기준(10)으로 초기화되므로 11 을 돌려준다.
        sequenceReturns(11L);

        assertThat(callNextId())
                .as("이미 존재하는 BG00010 을 다시 발급하면 안 된다")
                .isEqualTo("BG00011")
                .isNotEqualTo("BG00010");
    }

    @Test
    @DisplayName("금칙어가 있으면 번호를 낭비하지 않는다")
    void doesNotConsumeSequenceOnValidationFailure() throws Exception {
        // 길이 제한은 BalanceGameForm 의 애노테이션이 컨트롤러 단에서 잡는다.
        // 서비스까지 들어오는 검증은 금칙어 검사다.
        BalanceGameForm form = new BalanceGameForm();
        form.setTitle("시발 테스트");
        form.setOption1Text("A");
        form.setOption2Text("B");

        assertThatThrownBy(() -> service.insertBalanceGame(form, null, null))
                .isInstanceOf(RuntimeException.class);

        // 채번은 검증 통과 후에만 일어나야 한다.
        verify(balanceGameMapper, never()).nextBalanceGameSequence(anyMap());
    }
}
