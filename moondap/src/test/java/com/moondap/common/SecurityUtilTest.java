package com.moondap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdUserDTO;

/**
 * SecurityUtil 단위 테스트.
 *
 * 이 클래스가 잘못되면 밸런스 게임의 소유권 판정과 관리자 전용 기능이 통째로
 * 어긋나므로, 특히 "비로그인 시 null 반환" 계약을 고정해 둔다.
 * (예전 구현은 비로그인 사용자에게 "mdadmin" 을 돌려줬다)
 */
class SecurityUtilTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String role) {
        MdUserDTO user = new MdUserDTO();
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("ACTIVE");

        PrincipalDetails principal = new PrincipalDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private void loginAsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    }

    @Nested
    @DisplayName("인증되지 않은 사용자")
    class Unauthenticated {

        @Test
        @DisplayName("SecurityContext 가 비어 있으면 비인증으로 본다")
        void emptyContext() {
            SecurityContextHolder.clearContext();

            assertThat(SecurityUtil.isAuthenticated()).isFalse();
            assertThat(SecurityUtil.getCurrentUsername()).isNull();
            assertThat(SecurityUtil.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("익명 토큰은 인증된 것으로 취급하지 않는다")
        void anonymousTokenIsNotAuthenticated() {
            loginAsAnonymous();

            // AnonymousAuthenticationToken 도 isAuthenticated() 는 true 라서
            // 단순 위임하면 익명 사용자가 통과해 버린다.
            assertThat(SecurityUtil.isAuthenticated()).isFalse();
            assertThat(SecurityUtil.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("비로그인 시 username 은 null 이다 (mdadmin 이 아니다)")
        void anonymousUsernameIsNull() {
            loginAsAnonymous();

            assertThat(SecurityUtil.getCurrentUsername())
                    .as("익명 사용자가 특정 계정 소유로 기록되면 안 된다")
                    .isNull();
        }

        @Test
        @DisplayName("소유자가 필요한 자리에서는 예외를 던진다")
        void requireUsernameThrows() {
            loginAsAnonymous();

            assertThatThrownBy(SecurityUtil::requireCurrentUsername)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("일반 로그인 사용자")
    class NormalUser {

        @Test
        @DisplayName("username 을 반환하고 관리자는 아니다")
        void usernameAndNotAdmin() {
            loginAs("kyochan", "ROLE_USER");

            assertThat(SecurityUtil.isAuthenticated()).isTrue();
            assertThat(SecurityUtil.getCurrentUsername()).isEqualTo("kyochan");
            assertThat(SecurityUtil.requireCurrentUsername()).isEqualTo("kyochan");
            assertThat(SecurityUtil.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("본인 소유 콘텐츠만 접근 가능하다")
        void ownerOnly() {
            loginAs("kyochan", "ROLE_USER");

            assertThat(SecurityUtil.isAdminOrOwner("kyochan")).isTrue();
            assertThat(SecurityUtil.isAdminOrOwner("someoneElse")).isFalse();
        }

        @Test
        @DisplayName("소유자가 없는(익명) 콘텐츠는 일반 사용자가 건드릴 수 없다")
        void nullOwnerIsNotClaimable() {
            loginAs("kyochan", "ROLE_USER");

            assertThat(SecurityUtil.isAdminOrOwner(null))
                    .as("owner 가 null 인데 true 면 익명 콘텐츠를 아무나 가져간다")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("관리자")
    class Admin {

        @Test
        @DisplayName("소유자와 무관하게 접근 가능하다")
        void adminBypassesOwnership() {
            loginAs("mdadmin", "ROLE_ADMIN");

            assertThat(SecurityUtil.isAdmin()).isTrue();
            assertThat(SecurityUtil.isAdminOrOwner("someoneElse")).isTrue();
            assertThat(SecurityUtil.isAdminOrOwner(null)).isTrue();
        }
    }
}
