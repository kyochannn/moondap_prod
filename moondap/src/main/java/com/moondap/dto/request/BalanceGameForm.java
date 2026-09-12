package com.moondap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 밸런스 게임 등록/수정/삭제 요청 폼.
 *
 * <p>이전에는 {@code Map<String, String>} 으로 받았다. 그 방식의 문제는
 * 키 이름을 잘못 쓰면 컴파일도 통과하고 실행도 되는데 값만 조용히 null 이 된다는 점이다.
 * (실제로 서비스에서 {@code params.get("spicyFilter")} 를 쓰는데 화면에서는
 * 같은 이름을 쓰는지 확인할 방법이 코드상에 없었다)
 *
 * <p>길이 제한도 서비스 안 if 문에 흩어져 있던 것을 여기로 모았다.
 * 검증 실패 메시지는 GlobalExceptionHandler 가 400 으로 내보낸다.
 *
 * <p>필드 이름은 화면이 FormData 로 보내는 키와 정확히 일치해야 한다.
 * (balanceGame/insertBalanceGame.html, updateBalanceGame.html 참고)
 */
@Data
public class BalanceGameForm {

    /** 수정·삭제 시에만 사용한다. 등록 시에는 서버가 채번한다. */
    private String id;

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 30, message = "제목의 길이가 30자를 초과했습니다.")
    private String title;

    /** "true" / "false" 문자열로 전달된다. */
    private String spicyFilter;

    @NotBlank(message = "카테고리를 선택해 주세요.")
    private String category;

    /** draft / active / inactive. 미지정 시 서비스가 draft 로 둔다. */
    private String status;

    @NotBlank(message = "왼쪽 선택지를 입력해 주세요.")
    @Size(max = 20, message = "왼쪽 선택지의 길이가 20자를 초과했습니다.")
    private String option1Text;

    @NotBlank(message = "오른쪽 선택지를 입력해 주세요.")
    @Size(max = 20, message = "오른쪽 선택지의 길이가 20자를 초과했습니다.")
    private String option2Text;

    /** 수정 시 기존 이미지 경로. 새 이미지를 올리지 않으면 이 값을 유지한다. */
    private String oldOption1ImagePath;
    private String oldOption2ImagePath;

    public boolean isSpicy() {
        return Boolean.parseBoolean(spicyFilter);
    }

    public String getStatusOrDefault() {
        return (status == null || status.isBlank()) ? "draft" : status;
    }
}
