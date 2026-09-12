package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.moondap.common.FileService;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.CommentPageDTO;
import com.moondap.mapper.BalanceGameMapper;

/**
 * 댓글 페이징.
 *
 * <p>예전에는 LIMIT 없이 한 게임의 댓글을 전부 내려줬다. 댓글이 쌓일수록 한 번에
 * 받아야 하는 양이 늘어 화면이 무거워졌다.
 */
@ExtendWith(MockitoExtension.class)
class CommentPagingTest {

    private static final String GAME_ID = "BG00001";
    private static final int PAGE = StandardBalanceGameService.COMMENT_PAGE_SIZE;

    @Mock
    private BalanceGameMapper balanceGameMapper;

    @Mock
    private FileService fileService;

    @Mock
    private StatService statService;

    @InjectMocks
    private StandardBalanceGameService service;

    @Test
    @DisplayName("한 페이지 분량만 내려주고 남은 것이 있으면 알려준다")
    void limitsPageAndReportsMore() throws Exception {
        // 한 건 더 요청해 다음 페이지 존재를 판단하므로, 매퍼는 PAGE+1 개를 돌려준다.
        when(balanceGameMapper.selectBalanceGameComment(eq(GAME_ID), anyString(), eq(0), eq(PAGE + 1)))
                .thenReturn(comments(PAGE + 1));

        CommentPageDTO page = service.selectBalanceGameComment(GAME_ID, null, "latest", 0);

        assertThat(page.getComments()).hasSize(PAGE);
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextOffset()).isEqualTo(PAGE);
    }

    @Test
    @DisplayName("마지막 페이지에서는 더보기를 남기지 않는다")
    void lastPageHasNoMore() throws Exception {
        // 정확히 한 페이지만 남은 경우. 받은 개수로만 추측하면 빈 더보기가 한 번 더 뜬다.
        when(balanceGameMapper.selectBalanceGameComment(eq(GAME_ID), anyString(), eq(0), eq(PAGE + 1)))
                .thenReturn(comments(PAGE));

        CommentPageDTO page = service.selectBalanceGameComment(GAME_ID, null, "latest", 0);

        assertThat(page.getComments()).hasSize(PAGE);
        assertThat(page.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("댓글이 없으면 빈 페이지를 준다")
    void emptyPage() throws Exception {
        when(balanceGameMapper.selectBalanceGameComment(eq(GAME_ID), anyString(), eq(0), eq(PAGE + 1)))
                .thenReturn(new ArrayList<>());

        CommentPageDTO page = service.selectBalanceGameComment(GAME_ID, null, "latest", 0);

        assertThat(page.getComments()).isEmpty();
        assertThat(page.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("정렬 값이 매퍼까지 그대로 전달된다")
    void passesSortThrough() throws Exception {
        when(balanceGameMapper.selectBalanceGameComment(eq(GAME_ID), eq("popular"), eq(0), eq(PAGE + 1)))
                .thenReturn(new ArrayList<>());

        service.selectBalanceGameComment(GAME_ID, null, "popular", 0);

        verify(balanceGameMapper).selectBalanceGameComment(GAME_ID, "popular", 0, PAGE + 1);
    }

    @Test
    @DisplayName("음수 offset 은 0 으로 본다")
    void negativeOffsetIsClamped() throws Exception {
        when(balanceGameMapper.selectBalanceGameComment(eq(GAME_ID), anyString(), eq(0), eq(PAGE + 1)))
                .thenReturn(new ArrayList<>());

        CommentPageDTO page = service.selectBalanceGameComment(GAME_ID, null, "latest", -50);

        assertThat(page.getNextOffset()).isZero();
        verify(balanceGameMapper).selectBalanceGameComment(GAME_ID, "latest", 0, PAGE + 1);
    }

    private List<BalanceGameCommentDTO> comments(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    BalanceGameCommentDTO dto = new BalanceGameCommentDTO();
                    dto.setNo(i + 1);
                    dto.setQuestionId(GAME_ID);
                    dto.setContent("댓글 " + i);
                    return dto;
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
