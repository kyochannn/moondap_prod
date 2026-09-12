package com.moondap.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 댓글 삭제 요청.
 *
 * <p>삭제 권한은 요청에 담기지 않는다. 서버가 SecurityContext 와 익명 쿠키로 판정한다.
 */
@Data
public class CommentDeleteRequest {

    @NotNull(message = "대상 댓글이 지정되지 않았습니다.")
    private Integer no;
}
