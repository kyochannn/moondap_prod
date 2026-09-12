package com.moondap.common;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.moondap.config.auth.PrincipalDetails;

/**
 * SecurityContext 조회 유틸리티.
 *
 * 이전에는 BalanceGameController 와 StandardBalanceGameService 가 각자
 * IsLoggedIn() / getCurrentUserId() 를 복붙해 갖고 있었고, 관리자 판정도
 * 5개 파일 9곳에 흩어져 있었다. 판정 기준이 한 곳에서만 바뀌면 나머지가
 * 조용히 어긋나므로 여기로 모은다.
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 실제 로그인한 사용자인지 여부.
     * 익명 사용자도 Authentication 객체는 갖고 있으므로 isAuthenticated() 만으로는 부족하다.
     */
    public static boolean isAuthenticated() {
        Authentication auth = authentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * 현재 로그인한 사용자의 username.
     *
     * <p><b>비로그인 시 null 을 반환한다.</b> 예전에는 "mdadmin" 을 돌려줬는데,
     * 그 탓에 익명 사용자가 만든 콘텐츠가 관리자 계정 소유로 저장되고
     * 소유권 판정(CheckMyTest)까지 오염됐다.
     *
     * <p>호출부는 반드시 null 을 처리해야 한다. 소유자가 반드시 필요한 자리에서는
     * {@link #requireCurrentUsername()} 을 쓴다.
     */
    public static String getCurrentUsername() {
        if (!isAuthenticated()) {
            return null;
        }
        Authentication auth = authentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof PrincipalDetails) {
            return ((PrincipalDetails) principal).getUsername();
        }
        return auth.getName();
    }

    /**
     * 로그인이 전제인 자리에서 사용한다. 비로그인이면 예외를 던진다.
     */
    public static String requireCurrentUsername() {
        String username = getCurrentUsername();
        if (username == null) {
            throw new IllegalStateException("로그인이 필요한 작업입니다.");
        }
        return username;
    }

    /**
     * ROLE_ADMIN 보유 여부.
     */
    public static boolean isAdmin() {
        if (!isAuthenticated()) {
            return false;
        }
        return authentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * 관리자이거나 지정된 소유자 본인인지 여부.
     * owner 가 null 이면(= 소유자 없는 익명 콘텐츠) 관리자만 true 다.
     */
    public static boolean isAdminOrOwner(String owner) {
        if (isAdmin()) {
            return true;
        }
        String username = getCurrentUsername();
        return username != null && username.equals(owner);
    }
}
