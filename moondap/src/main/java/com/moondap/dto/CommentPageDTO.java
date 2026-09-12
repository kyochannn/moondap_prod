package com.moondap.dto;

import java.util.List;

import lombok.Data;

/**
 * 댓글 한 페이지.
 *
 * <p>목록만 내려주면 화면이 "더 남았는지"를 알 수 없다. 받아온 개수로 추측하면
 * 마지막 페이지가 정확히 한도와 같을 때 빈 더보기를 한 번 더 누르게 된다.
 */
@Data
public class CommentPageDTO {

    private List<BalanceGameCommentDTO> comments;

    /** 더 받아올 댓글이 남았는지. */
    private boolean hasMore;

    /** 다음 더보기에 쓸 offset. */
    private int nextOffset;

    public static CommentPageDTO of(List<BalanceGameCommentDTO> comments, boolean hasMore, int nextOffset) {
        CommentPageDTO page = new CommentPageDTO();
        page.setComments(comments);
        page.setHasMore(hasMore);
        page.setNextOffset(nextOffset);
        return page;
    }
}
