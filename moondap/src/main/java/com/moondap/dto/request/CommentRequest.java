package com.moondap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 밸런스 게임 댓글 등록 요청 (JSON).
 *
 * <p>화면이 보내는 키: {@code {"id", "nickname", "side", "content"}}
 *
 * <p><b>userId 필드는 일부러 두지 않는다.</b> 예전에는 요청 본문의 userId 를 그대로
 * 저장해서, 비로그인 사용자가 userId="mdadmin" 을 실어 보내면 관리자 명의 댓글을
 * 만들 수 있었다. 작성자는 서버가 SecurityContext 에서만 결정한다.
 */
@Data
public class CommentRequest {

    @NotBlank(message = "댓글 대상이 지정되지 않았습니다.")
    private String id;

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(max = 20, message = "닉네임은 20자 이내로 입력해 주세요.")
    private String nickname;

    @NotBlank(message = "투표 후 댓글을 남길 수 있습니다.")
    private String side;

    @NotBlank(message = "메시지를 입력해 주세요.")
    @Size(max = 50, message = "댓글은 50자 이내로 입력 가능합니다.")
    private String content;

    /** 작성자. 요청 본문이 아니라 서버가 채운다. 비로그인이면 null 이다. */
    private String userId;

    /** 익명 작성자 식별 토큰. 요청 본문이 아니라 서버가 쿠키에서 채운다. */
    private String anonId;
}
