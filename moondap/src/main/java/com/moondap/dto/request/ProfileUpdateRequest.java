package com.moondap.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 사용자 본인의 프로필 수정 요청.
 *
 * <p><b>이 DTO에 role · status · point · username 이 없는 것은 의도된 것이다.</b>
 *
 * <p>이전에는 {@code MdUserDTO} 를 그대로 바인딩했다. 그 DTO 에는 role/status/point 가
 * 있고 매퍼의 UPDATE 문이 그 값들을 그대로 반영했기 때문에 두 가지 문제가 있었다.
 *
 * <ul>
 *   <li>악용: {@code role=ROLE_ADMIN} 을 함께 보내면 관리자로 승격됐다.</li>
 *   <li>정상 사용: 화면이 nickname/email/bio 만 보내므로 나머지가 null 로 덮어써져
 *       status 가 사라지고 {@code isEnabled()} 가 false 가 되어 로그인이 막혔다.</li>
 * </ul>
 *
 * <p>바인딩 대상 자체를 좁히면 "서버에서 덮어쓰는 것을 한 줄 빠뜨려서" 다시 뚫리는 일이 없다.
 */
@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(max = 20, message = "닉네임은 20자 이내로 입력해 주세요.")
    private String nickname;

    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일이 너무 깁니다.")
    private String email;

    @Size(max = 500, message = "자기소개는 500자 이내로 입력해 주세요.")
    private String bio;
}
