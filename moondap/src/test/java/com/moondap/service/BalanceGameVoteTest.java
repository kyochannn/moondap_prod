package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.moondap.dto.BalanceGameDTO;
import com.moondap.dto.request.VoteRequest;
import com.moondap.mapper.BalanceGameMapper;

/**
 * 투표 집계 테스트.
 *
 * <p>두 가지 규칙이 동시에 성립해야 한다.
 *
 * <ul>
 *   <li>한 사람의 표는 하나다 — 여러 번 눌러도 총 투표수가 늘지 않는다.</li>
 *   <li>마음은 바꿀 수 있다 — 화면의 "다시하기" 는 localStorage 만 지우기 때문에,
 *       서버가 재투표를 막으면 화면과 서버가 어긋난다. 실제로 A 를 골랐다가 B 로 바꾸면
 *       화면은 B 를 보여주는데 서버 기록은 A 라서 댓글이 A 쪽에 달렸다.</li>
 * </ul>
 *
 * <p>그래서 재투표는 "새 표 추가"가 아니라 "기존 표 이동"으로 처리한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceGameVoteTest {

    private static final String GAME_ID = "BG00001";
    private static final String VOTER = "ip:203.0.113.7";

    @Mock
    private BalanceGameMapper balanceGameMapper;

    @Mock
    private StatService statService;

    @InjectMocks
    private StandardBalanceGameService service;

    private VoteRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = new VoteRequest();
        request.setId(GAME_ID);
        request.setSide("left");

        BalanceGameDTO game = new BalanceGameDTO();
        game.setId(GAME_ID);
        game.setOption1Count(3);
        game.setOption2Count(1);
        when(balanceGameMapper.selectBalanceGame(eq(GAME_ID), any(), any())).thenReturn(game);
        when(balanceGameMapper.applyVote(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(1);
    }

    /** 처음 투표하는 상황 */
    private void asFirstVote() {
        when(balanceGameMapper.selectVotedSide(GAME_ID, VOTER)).thenReturn(null);
        when(balanceGameMapper.insertVoteLog(eq(GAME_ID), eq(VOTER), anyString())).thenReturn(1);
    }

    /** 이미 previousSide 에 투표해 둔 상황 */
    private void asExistingVote(String previousSide) {
        when(balanceGameMapper.selectVotedSide(GAME_ID, VOTER)).thenReturn(previousSide);
        when(balanceGameMapper.insertVoteLog(eq(GAME_ID), eq(VOTER), anyString())).thenReturn(0);
    }

    @Nested
    @DisplayName("첫 투표")
    class FirstVote {

        @Test
        @DisplayName("선택한 진영과 총 투표수가 함께 증가한다")
        void countsAndTotalIncrease() throws Exception {
            asFirstVote();

            service.vote(request, VOTER);

            verify(balanceGameMapper).applyVote(GAME_ID, 1, 0, 1);
            verify(statService, times(1)).incrementParticipationCount();
        }

        @Test
        @DisplayName("right 를 고르면 두 번째 선택지가 증가한다")
        void rightVote() throws Exception {
            asFirstVote();
            request.setSide("right");

            service.vote(request, VOTER);

            verify(balanceGameMapper).applyVote(GAME_ID, 0, 1, 1);
        }
    }

    @Nested
    @DisplayName("재투표 (마음 바꾸기)")
    class ChangeVote {

        @Test
        @DisplayName("[회귀] left 에서 right 로 바꾸면 표가 이동한다")
        void movesVoteFromLeftToRight() throws Exception {
            asExistingVote("left");
            request.setSide("right");

            service.vote(request, VOTER);

            // 이전 진영 -1, 새 진영 +1, 총 투표수는 그대로.
            verify(balanceGameMapper).applyVote(GAME_ID, -1, 1, 0);
            verify(balanceGameMapper).updateVoteLogSide(GAME_ID, VOTER, "right");
        }

        @Test
        @DisplayName("right 에서 left 로도 대칭으로 동작한다")
        void movesVoteFromRightToLeft() throws Exception {
            asExistingVote("right");
            request.setSide("left");

            service.vote(request, VOTER);

            verify(balanceGameMapper).applyVote(GAME_ID, 1, -1, 0);
            verify(balanceGameMapper).updateVoteLogSide(GAME_ID, VOTER, "left");
        }

        @Test
        @DisplayName("재투표는 총 투표수를 늘리지 않는다")
        void doesNotInflateTotal() throws Exception {
            asExistingVote("left");
            request.setSide("right");

            service.vote(request, VOTER);

            // totalDelta 가 1 인 호출이 있으면 한 사람이 두 표를 갖게 된다.
            verify(balanceGameMapper, never()).applyVote(anyString(), anyInt(), anyInt(), eq(1));
        }

        @Test
        @DisplayName("재투표는 참여수 통계를 올리지 않는다")
        void doesNotCountAsNewParticipation() throws Exception {
            asExistingVote("left");
            request.setSide("right");

            service.vote(request, VOTER);

            // 새로운 참여가 아니라 기존 표의 이동이다.
            verify(statService, never()).incrementParticipationCount();
        }

        @Test
        @DisplayName("같은 진영을 다시 누르면 아무것도 바뀌지 않는다")
        void sameSideIsNoOp() throws Exception {
            asExistingVote("left");
            request.setSide("left");

            BalanceGameDTO result = service.vote(request, VOTER);

            assertThat(result).isNotNull();
            verify(balanceGameMapper, never()).applyVote(anyString(), anyInt(), anyInt(), anyInt());
            verify(statService, never()).incrementParticipationCount();
        }
    }

    @Nested
    @DisplayName("진영 미기록 예전 행")
    class LegacyRow {

        @Test
        @DisplayName("집계는 건드리지 않고 기록만 채운다")
        void backfillsWithoutTouchingCounts() throws Exception {
            // selected_side 컬럼 추가 이전에 투표한 행.
            // 그 표가 어느 컬럼에 들어갔는지 알 수 없어 옮길 수 없다.
            asExistingVote(null);
            request.setSide("right");

            service.vote(request, VOTER);

            verify(balanceGameMapper).updateVoteLogSide(GAME_ID, VOTER, "right");
            verify(balanceGameMapper, never()).applyVote(anyString(), anyInt(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        @DisplayName("[회귀] side 가 비어 있으면 right 로 집계되지 않는다")
        void blankSideIsNotCountedAsRight() throws Exception {
            // @Pattern 은 null 을 통과시킨다. DTO 에 @NotBlank 를 더했고
            // 서비스에서도 한 번 더 막는다.
            asFirstVote();

            for (String bad : new String[] { null, "", "  ", "middle", "LEFT" }) {
                request.setSide(bad);

                assertThat(service.vote(request, VOTER))
                        .as("side=%s 는 투표로 집계되면 안 된다", bad)
                        .isNull();
            }

            verify(balanceGameMapper, never()).insertVoteLog(anyString(), anyString(), anyString());
            verify(balanceGameMapper, never()).applyVote(anyString(), anyInt(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("투표자 식별자가 없으면 투표하지 않는다")
        void rejectsMissingVoterKey() throws Exception {
            assertThat(service.vote(request, null)).isNull();
            assertThat(service.vote(request, "  ")).isNull();

            verify(balanceGameMapper, never()).insertVoteLog(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("게임 ID 가 없으면 투표하지 않는다")
        void rejectsMissingGameId() throws Exception {
            request.setId(null);

            assertThat(service.vote(request, VOTER)).isNull();

            verify(balanceGameMapper, never()).insertVoteLog(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("대상 게임이 없으면 예외를 던져 로그까지 롤백시킨다")
        void rollsBackWhenGameMissing() throws Exception {
            asFirstVote();
            when(balanceGameMapper.applyVote(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(0);

            // 로그만 남고 집계는 안 되면 그 사용자는 영영 투표할 수 없게 된다.
            assertThatThrownBy(() -> service.vote(request, VOTER))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
