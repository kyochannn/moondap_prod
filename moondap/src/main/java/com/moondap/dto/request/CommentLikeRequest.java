package com.moondap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 댓글 좋아요 증감 요청.
 *
 * <p>화면이 보내는 키: {@code {"no", "id", "setting"}}
 *
 * <p>{@code no} 를 Integer 로 받는 이유: 이전에는 문자열을 서비스에서
 * {@code Integer.parseInt} 로 변환해서, 숫자가 아닌 값이 오면
 * NumberFormatException 이 그대로 터졌다. 이제 바인딩 단계에서 걸러진다.
 */
@Data
public class CommentLikeRequest {

    @NotBlank(message = "대상 게임이 지정되지 않았습니다.")
    private String id;

    @NotNull(message = "대상 댓글이 지정되지 않았습니다.")
    private Integer no;

    /** "UP" 이면 +1, 그 밖이면 -1 */
    private String setting;

    public int toDelta() {
        return "UP".equals(setting) ? 1 : -1;
    }
}
