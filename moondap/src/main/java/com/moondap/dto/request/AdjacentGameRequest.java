package com.moondap.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 이전/다음 밸런스 게임 ID 조회 요청.
 *
 * <p>화면이 보내는 키: {@code {"id", "direction", "spicyFilter", "category"}}
 */
@Data
public class AdjacentGameRequest {

    @NotBlank(message = "기준 게임이 지정되지 않았습니다.")
    private String id;

    /** "PREV" 면 이전, 그 밖이면 다음 */
    private String direction;

    /** 현재 목록의 매운맛 필터를 그대로 유지하기 위해 함께 받는다. */
    private String spicyFilter;

    /** 현재 목록의 카테고리 필터 */
    private String category;
}
