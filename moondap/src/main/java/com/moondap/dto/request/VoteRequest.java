package com.moondap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 밸런스 게임 투표 요청 (JSON).
 *
 * <p>화면이 보내는 키: {@code {"id": "BG00001", "side": "left"}}
 */
@Data
public class VoteRequest {

    @NotBlank(message = "투표 대상이 지정되지 않았습니다.")
    private String id;

    /**
     * 선택한 진영.
     *
     * <p>이전에는 어떤 문자열이든 받아서, left/right 가 아니면 양쪽 카운트가 모두 0 인
     * 무의미한 투표가 기록될 수 있었다. 여기서 형식을 고정한다.
     *
     * <p><b>@Pattern 만으로는 부족하다.</b> @Pattern 은 null 을 통과시키기 때문에
     * side 를 아예 빼고 보내면 검증을 지나가고, isLeft() 가 false 가 되어
     * right 투표로 집계된다. @NotBlank 를 반드시 함께 둔다.
     */
    @NotBlank(message = "선택지를 선택해 주세요.")
    @Pattern(regexp = "left|right", message = "선택지 값이 올바르지 않습니다.")
    private String side;

    public boolean isLeft() {
        return "left".equals(side);
    }
}
