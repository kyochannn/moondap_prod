package com.moondap.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 댓글 목록 조회 요청.
 *
 * <p>화면이 보내는 키: {@code {"id": "BG00001", "sort": "popular", "offset": 0}}
 */
@Data
public class CommentListRequest {

    @NotBlank(message = "조회 대상이 지정되지 않았습니다.")
    private String id;

    /**
     * "popular" 면 좋아요순, 그 외(null 포함)에는 최신순.
     *
     * <p>값을 열거형으로 좁히지 않고 서비스에서 "popular" 만 가려낸다. 정렬은 못 알아들으면
     * 기본값으로 떨어지면 그만이라, 오타 하나로 화면 전체가 오류가 되는 편이 더 나쁘다.
     */
    private String sort;

    /** 이미 받아간 댓글 수. 더보기를 누를 때마다 늘어난다. */
    @Min(value = 0, message = "잘못된 요청입니다.")
    private int offset;
}
