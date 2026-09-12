package com.moondap.config.auth;

import java.util.List;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 중인 세션을 강제로 만료시킨다.
 *
 * <p>왜 필요한가 — {@link PrincipalDetails#isEnabled()} 는 <b>로그인 시점에만</b> 평가된다.
 * 관리자가 사용자를 정지(SUSPENDED)하거나 삭제(DELETED)해도, 그 사용자가 이미 로그인해
 * 두었다면 세션이 살아 있는 동안 아무 제약 없이 서비스를 계속 쓸 수 있었다.
 *
 * <p>SessionRegistry 에 등록된 세션 중 해당 계정의 것을 찾아 만료 표시를 남기면,
 * 다음 요청에서 시큐리티가 세션을 끊는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveSessionService {

    private final SessionRegistry sessionRegistry;

    /**
     * 해당 사용자의 모든 세션을 만료시킨다.
     *
     * @return 만료 처리한 세션 수
     */
    public int expireSessions(String username) {
        if (username == null) {
            return 0;
        }

        int expired = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!username.equals(resolveUsername(principal))) {
                continue;
            }

            // includeExpiredSessions=false : 이미 만료된 것은 다시 건드리지 않는다.
            List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
            for (SessionInformation session : sessions) {
                session.expireNow();
                expired++;
            }
        }

        if (expired > 0) {
            log.info("세션 강제 만료: username={}, 세션 {}개", username, expired);
        }
        return expired;
    }

    private String resolveUsername(Object principal) {
        if (principal instanceof PrincipalDetails details) {
            return details.getUsername();
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails details) {
            return details.getUsername();
        }
        return String.valueOf(principal);
    }
}
