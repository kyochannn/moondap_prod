package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.moondap.dto.MdUserDTO;
import com.moondap.dto.request.ProfileUpdateRequest;
import com.moondap.mapper.MdUserMapper;

/**
 * 프로필 수정의 권한 경계 테스트.
 *
 * <p>이전 구현은 요청에서 바인딩한 MdUserDTO 를 그대로 UPDATE 에 넘겼다.
 * 매퍼가 {@code SET role=#{user.role}, point=..., status=...} 였기 때문에:
 *
 * <ul>
 *   <li>role=ROLE_ADMIN 을 실어 보내면 관리자로 승격됐고,</li>
 *   <li>화면이 보내지 않는 status 는 null 로 지워져 isEnabled() 가 false 가 되면서
 *       프로필을 수정한 사용자가 로그인할 수 없게 됐다.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileUpdateSecurityTest {

    private static final String USERNAME = "kyochan";

    @Mock
    private MdUserMapper mdUserMapper;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @InjectMocks
    private StandardMdUserService service;

    private MdUserDTO stored;

    @BeforeEach
    void setUp() {
        stored = new MdUserDTO();
        stored.setUsername(USERNAME);
        stored.setNickname("기존닉네임");
        stored.setEmail("old@example.com");
        stored.setBio("기존 소개");
        stored.setRole("ROLE_USER");
        stored.setStatus("ACTIVE");
        stored.setPoint(100);
        stored.setProfileImage("old.png");
        stored.setPassword("$2a$10$existinghash");

        when(mdUserMapper.selectUserName(USERNAME)).thenReturn(stored);
        when(mdUserMapper.countByNickname(anyString())).thenReturn(0);
    }

    private MdUserDTO captureSaved() {
        ArgumentCaptor<MdUserDTO> captor = ArgumentCaptor.forClass(MdUserDTO.class);
        verify(mdUserMapper).updateUser(captor.capture());
        return captor.getValue();
    }

    private ProfileUpdateRequest form(String nickname, String email, String bio) {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickname(nickname);
        request.setEmail(email);
        request.setBio(bio);
        return request;
    }

    // ── 권한 상승 차단 ─────────────────────────────────────────

    @Test
    @DisplayName("[회귀] 프로필 수정으로 role 을 바꿀 수 없다")
    void cannotEscalateRole() {
        service.updateProfile(USERNAME, form("새닉네임", "new@example.com", "새 소개"), null);

        assertThat(captureSaved().getRole())
                .as("요청에 role 을 실어 보낼 방법 자체가 없어야 한다")
                .isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("[회귀] 프로필 수정이 status 를 지우지 않는다")
    void doesNotWipeStatus() {
        // status 가 null 이 되면 isEnabled() 가 false 가 되어 로그인이 영구히 막혔다.
        service.updateProfile(USERNAME, form("새닉네임", "new@example.com", "새 소개"), null);

        assertThat(captureSaved().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[회귀] 프로필 수정이 point 를 지우지 않는다")
    void doesNotWipePoint() {
        service.updateProfile(USERNAME, form("새닉네임", "new@example.com", "새 소개"), null);

        assertThat(captureSaved().getPoint()).isEqualTo(100);
    }

    // ── 정상 동작 ──────────────────────────────────────────────

    @Test
    @DisplayName("허용된 필드는 정상적으로 반영된다")
    void updatesAllowedFields() {
        service.updateProfile(USERNAME, form("새닉네임", "new@example.com", "새 소개"), null);

        MdUserDTO saved = captureSaved();
        assertThat(saved.getNickname()).isEqualTo("새닉네임");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getBio()).isEqualTo("새 소개");
    }

    @Test
    @DisplayName("새 이미지를 올리지 않으면 기존 이미지를 유지한다")
    void keepsExistingImageWhenNotUploaded() {
        service.updateProfile(USERNAME, form("새닉네임", "a@b.com", "소개"), null);

        assertThat(captureSaved().getProfileImage()).isEqualTo("old.png");
    }

    @Test
    @DisplayName("새 이미지를 올리면 교체한다")
    void replacesImageWhenUploaded() {
        service.updateProfile(USERNAME, form("새닉네임", "a@b.com", "소개"), "new.png");

        assertThat(captureSaved().getProfileImage()).isEqualTo("new.png");
    }

    @Test
    @DisplayName("프로필 수정은 비밀번호 컬럼을 건드리지 않는다")
    void doesNotTouchPassword() {
        service.updateProfile(USERNAME, form("새닉네임", "a@b.com", "소개"), null);

        // 매퍼의 <if test="user.password != null and != ''"> 가 SET 절을 건너뛰게 한다.
        assertThat(captureSaved().getPassword()).isNull();
    }

    // ── 관리자 경로 ────────────────────────────────────────────

    @Test
    @DisplayName("관리자는 role 과 status 를 바꿀 수 있다")
    void adminCanChangeRoleAndStatus() {
        MdUserDTO input = new MdUserDTO();
        input.setUsername(USERNAME);
        input.setRole("ROLE_ADMIN");
        input.setStatus("SUSPENDED");

        service.updateUserByAdmin(input);

        MdUserDTO saved = captureSaved();
        assertThat(saved.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(saved.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("[회귀] 관리자 수정이 전송되지 않은 bio 를 지우지 않는다")
    void adminUpdateKeepsBio() {
        // 관리 화면 폼에 bio 입력란이 없어서, 관리자가 수정할 때마다 자기소개가 지워졌다.
        MdUserDTO input = new MdUserDTO();
        input.setUsername(USERNAME);
        input.setRole("ROLE_ADMIN");

        service.updateUserByAdmin(input);

        assertThat(captureSaved().getBio()).isEqualTo("기존 소개");
    }
}
