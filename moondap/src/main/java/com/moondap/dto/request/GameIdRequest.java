package com.moondap.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 게임 ID 하나만 필요한 조회 요청.
 *
 * <p>투표 현황 조회, 댓글 목록 조회에서 사용한다.
 * 화면이 보내는 키: {@code {"id": "BG00001"}}
 */
@Data
public class GameIdRequest {

    @NotBlank(message = "조회 대상이 지정되지 않았습니다.")
    private String id;
}
