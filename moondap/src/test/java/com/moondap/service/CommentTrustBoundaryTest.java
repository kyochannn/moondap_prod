package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.moondap.common.exception.UserMessageException;
import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.MdUserDTO;
import com.moondap.dto.request.CommentLikeRequest;
import com.moondap.dto.request.CommentRequest;
import com.moondap.mapper.BalanceGameMapper;

/**
 * 댓글의 신뢰 경계 테스트.
 *
 * <p>이전에는 진영·좋아요 중복 방지·작성자 식별이 전부 브라우저(localStorage)에 있었다.
 * 투표는 이미 서버가 판정하는데 댓글만 예전 방식이라 다음이 가능했다.
 *
 * <ul>
 *   <li>투표하지 않았거나 반대편에 투표하고도 원하는 진영으로 댓글 작성</li>
 *   <li>localStorage 를 지우고 같은 댓글에 좋아요 무한 증가</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentTrustBoundaryTest {

    private static final String GAME_ID = "BG00001";
    private static final String VOTER = "ip:203.0.113.7";
    private static final int COMMENT_NO = 42;

    @Mock
    private BalanceGameMapper balanceGameMapper;

    @Mock
    private com.moondap.common.FileService fileService;

    @Mock
    private StatService statService;

    @InjectMocks
    private StandardBalanceGameService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String role) {
        MdUserDTO user = new MdUserDTO();
        user.setUsername(username);
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new PrincipalDetails(user), null, List.of(new SimpleGrantedAuthority(role))));
    }

    private CommentRequest commentRequest(String clientSide) {
        CommentRequest request = new CommentRequest();
        request.setId(GAME_ID);
        request.setNickname("tester");
        request.setContent("내용");
        request.setSide(clientSide);
        return request;
    }

    private BalanceGameCommentDTO storedComment() {
        BalanceGameCommentDTO c = new BalanceGameCommentDTO();
        c.setNo(COMMENT_NO);
        c.setQuestionId(GAME_ID);
        c.setLikeCount(5);
        return c;
    }

    @BeforeEach
    void setUp() {
        when(balanceGameMapper.insertBalanceGameComment(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    ((BalanceGameCommentDTO) inv.getArgument(0)).setNo(COMMENT_NO);
                    return 1;
                });
        when(balanceGameMapper.selectCommentByNo(COMMENT_NO)).thenReturn(storedComment());
    }

    // ── ② 진영은 서버의 투표 기록이 결정한다 ───────────────────

    @Test
    @DisplayName("[회귀] 화면이 보낸 진영이 아니라 실제 투표한 진영으로 저장된다")
    void sideComesFromVoteLogNotRequest() throws Exception {
        when(balanceGameMapper.selectVotedSide(GAME_ID, VOTER)).thenReturn("left");

        // 화면은 right 라고 보냈지만 실제 투표는 left 였다.
        service.insertBalanceGameComment(commentRequest("right"), VOTER);

        ArgumentCaptor<BalanceGameCommentDTO> captor = ArgumentCaptor.forClass(BalanceGameCommentDTO.class);
        verify(balanceGameMapper).insertBalanceGameComment(captor.capture());
        assertThat(captor.getValue().getSelectedSide()).isEqualTo("left");
    }

    @Test
    @DisplayName("[회귀] 투표하지 않았으면 댓글을 쓸 수 없다")
    void rejectsCommentWithoutVote() {
        when(balanceGameMapper.selectVotedSide(GAME_ID, VOTER)).thenReturn(null);
        when(balanceGameMapper.countVoteLog(GAME_ID, VOTER)).thenReturn(0);

        assertThatThrownBy(() -> service.insertBalanceGameComment(commentRequest("left"), VOTER))
                .isInstanceOf(UserMessageException.class)
                .hasMessageContaining("투표 후");

        verify(balanceGameMapper, never()).insertBalanceGameComment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("진영 미기록 예전 투표는 화면 값으로 한 번 보정된다")
    void backfillsLegacyVoteSide() throws Exception {
        // selected_side 컬럼 추가 이전에 투표한 사용자
        when(balanceGameMapper.selectVotedSide(GAME_ID, VOTER)).thenReturn(null);
        when(balanceGameMapper.countVoteLog(GAME_ID, VOTER)).thenReturn(1);

        service.insertBalanceGameComment(commentRequest("right"), VOTER);

        verify(balanceGameMapper).updateVoteLogSide(GAME_ID, VOTER, "right");
    }

    @Test
    @DisplayName("보정 시에도 left/right 가 아닌 값은 거절한다")
    void rejectsInvalidSideOnBackfill() {
        when(balanceGameMapper.selectVotedSide(GAME_ID, VOTER)).thenReturn(null);
        when(balanceGameMapper.countVoteLog(GAME_ID, VOTER)).thenReturn(1);

        assertThatThrownBy(() -> service.insertBalanceGameComment(commentRequest("middle"), VOTER))
                .isInstanceOf(UserMessageException.class);
    }

    // ── ① 좋아요는 서버 기록이 판정한다 ────────────────────────

    @Test
    @DisplayName("처음 누르면 +1 되고 눌린 상태가 된다")
    void firstLikeIncrements() throws Exception {
        when(balanceGameMapper.insertCommentLike(COMMENT_NO, VOTER)).thenReturn(1);

        CommentLikeRequest request = new CommentLikeRequest();
        request.setId(GAME_ID);
        request.setNo(COMMENT_NO);

        BalanceGameCommentDTO result = service.toggleCommentLike(request, VOTER);

        verify(balanceGameMapper).updateBalanceGameCommentLikeCount(COMMENT_NO, GAME_ID, 1);
        assertThat(result.isLikedByMe()).isTrue();
    }

    @Test
    @DisplayName("[회귀] 이미 눌렀다면 다시 눌러도 증가하지 않고 취소된다")
    void secondLikeCancelsInsteadOfIncrementing() throws Exception {
        // INSERT IGNORE 가 0 = 이미 기록이 있음
        when(balanceGameMapper.insertCommentLike(COMMENT_NO, VOTER)).thenReturn(0);
        when(balanceGameMapper.deleteCommentLike(COMMENT_NO, VOTER)).thenReturn(1);

        CommentLikeRequest request = new CommentLikeRequest();
        request.setId(GAME_ID);
        request.setNo(COMMENT_NO);

        BalanceGameCommentDTO result = service.toggleCommentLike(request, VOTER);

        // 예전에는 화면이 보낸 setting 을 믿고 ±1 해서, localStorage 를 지우면
        // 같은 댓글에 좋아요를 무한히 올릴 수 있었다.
        verify(balanceGameMapper, never()).updateBalanceGameCommentLikeCount(anyInt(), anyString(), eq(1));
        verify(balanceGameMapper).updateBalanceGameCommentLikeCount(COMMENT_NO, GAME_ID, -1);
        assertThat(result.isLikedByMe()).isFalse();
    }

    // ── ③ 삭제 권한 ───────────────────────────────────────────

    @Test
    @DisplayName("[회귀] 로그인 작성자는 본인 댓글을 지울 수 있다")
    void authorCanDeleteOwnComment() throws Exception {
        loginAs("kyochan", "ROLE_USER");
        BalanceGameCommentDTO mine = storedComment();
        mine.setUserId("kyochan");
        when(balanceGameMapper.selectCommentByNo(COMMENT_NO)).thenReturn(mine);
        when(balanceGameMapper.deleteSingleComment(COMMENT_NO)).thenReturn(1);

        assertThat(service.deleteSingleComment(COMMENT_NO)).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("남의 댓글은 지울 수 없다")
    void cannotDeleteOthersComment() throws Exception {
        loginAs("kyochan", "ROLE_USER");
        BalanceGameCommentDTO others = storedComment();
        others.setUserId("someoneElse");
        when(balanceGameMapper.selectCommentByNo(COMMENT_NO)).thenReturn(others);

        assertThatThrownBy(() -> service.deleteSingleComment(COMMENT_NO))
                .isInstanceOf(UserMessageException.class);

        verify(balanceGameMapper, never()).deleteSingleComment(anyInt());
    }

    @Test
    @DisplayName("관리자는 누구의 댓글이든 지울 수 있다")
    void adminCanDeleteAny() throws Exception {
        loginAs("mdadmin", "ROLE_ADMIN");
        BalanceGameCommentDTO others = storedComment();
        others.setUserId("someoneElse");
        when(balanceGameMapper.selectCommentByNo(COMMENT_NO)).thenReturn(others);
        when(balanceGameMapper.deleteSingleComment(COMMENT_NO)).thenReturn(1);

        assertThat(service.deleteSingleComment(COMMENT_NO)).isEqualTo("SUCCESS");
    }

    // ── ④ 응답은 변경된 댓글 하나 ──────────────────────────────

    @Test
    @DisplayName("[회귀] 댓글 작성 응답이 전체 목록이 아니라 생성된 댓글 하나다")
    void insertReturnsSingleComment() throws Exception {
        when(balanceGameMapper.selectVotedSide(GAME_ID, VOTER)).thenReturn("left");

        BalanceGameCommentDTO created = service.insertBalanceGameComment(commentRequest("left"), VOTER);

        assertThat(created).isNotNull();
        assertThat(created.getNo()).isEqualTo(COMMENT_NO);
        // 좋아요 하나에도 전체 목록을 다시 읽던 동작을 없앴다.
        verify(balanceGameMapper, never()).selectBalanceGameComment(anyString());
    }
}
