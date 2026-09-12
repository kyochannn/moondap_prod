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
 * 투표 중복 방지 로직 테스트.
 *
 * 중복 판정의 진실은 DB 의 UNIQUE(question_id, voter_key) 제약이고,
 * 여기서는 "INSERT IGNORE 가 0을 돌려줬을 때 서비스가 어떻게 행동하는가" 를 고정한다.
 * 투표수는 메인 화면의 인기 순위를 결정하므로 조작되면 곧바로 노출 순서가 왜곡된다.
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
    }

    @Test
    @DisplayName("첫 투표는 집계되고 참여수도 증가한다")
    void firstVoteCounts() throws Exception {
        when(balanceGameMapper.insertVoteLog(GAME_ID, VOTER, "left")).thenReturn(1);
        when(balanceGameMapper.vote(eq(GAME_ID), anyInt(), anyInt())).thenReturn(1);

        BalanceGameDTO result = service.vote(request, VOTER);

        assertThat(result).isNotNull();
        verify(balanceGameMapper).vote(GAME_ID, 1, 0);
        verify(statService, times(1)).incrementParticipationCount();
    }

    @Test
    @DisplayName("중복 투표는 집계되지 않는다")
    void duplicateVoteIsNotCounted() throws Exception {
        // INSERT IGNORE 가 0 = 이미 같은 (게임, 투표자) 조합이 존재
        when(balanceGameMapper.insertVoteLog(GAME_ID, VOTER, "left")).thenReturn(0);

        BalanceGameDTO result = service.vote(request, VOTER);

        verify(balanceGameMapper, never()).vote(anyString(), anyInt(), anyInt());
        assertThat(result)
                .as("오류가 아니라 현재 집계를 돌려줘야 화면이 정상 동작한다")
                .isNotNull();
    }

    @Test
    @DisplayName("중복 투표는 참여수 통계도 올리지 않는다")
    void duplicateVoteDoesNotInflateStats() throws Exception {
        when(balanceGameMapper.insertVoteLog(GAME_ID, VOTER, "left")).thenReturn(0);

        service.vote(request, VOTER);

        // 참여수 증가가 컨트롤러에 있던 시절에는 중복 투표도 통계를 올렸다.
        verify(statService, never()).incrementParticipationCount();
    }

    @Test
    @DisplayName("right 투표는 두 번째 선택지에 집계된다")
    void rightVote() throws Exception {
        request.setSide("right");
        when(balanceGameMapper.insertVoteLog(GAME_ID, VOTER, "right")).thenReturn(1);
        when(balanceGameMapper.vote(eq(GAME_ID), anyInt(), anyInt())).thenReturn(1);

        service.vote(request, VOTER);

        verify(balanceGameMapper).vote(GAME_ID, 0, 1);
    }

    @Test
    @DisplayName("[회귀] side 가 비어 있으면 right 로 집계되지 않는다")
    void blankSideIsNotCountedAsRight() throws Exception {
        // @Pattern 은 null 을 통과시킨다. 그래서 side 를 빼고 보내면 검증을 지나가고
        // isLeft() 가 false 가 되어 right 투표로 집계됐다.
        // DTO 에 @NotBlank 를 더했고, 서비스에서도 한 번 더 막는다.
        for (String bad : new String[] { null, "", "  ", "middle", "LEFT" }) {
            request.setSide(bad);

            assertThat(service.vote(request, VOTER))
                    .as("side=%s 는 투표로 집계되면 안 된다", bad)
                    .isNull();
        }

        verify(balanceGameMapper, never()).insertVoteLog(anyString(), anyString(), anyString());
        verify(balanceGameMapper, never()).vote(anyString(), anyInt(), anyInt());
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
        when(balanceGameMapper.insertVoteLog(GAME_ID, VOTER, "left")).thenReturn(1);
        when(balanceGameMapper.vote(eq(GAME_ID), anyInt(), anyInt())).thenReturn(0);

        // 로그만 남고 집계는 안 되면, 그 사용자는 영영 투표할 수 없게 된다.
        assertThatThrownBy(() -> service.vote(request, VOTER))
                .isInstanceOf(IllegalStateException.class);
    }
}
