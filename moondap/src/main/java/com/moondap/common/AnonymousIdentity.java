package com.moondap.common;

import java.util.UUID;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 비로그인 사용자를 구분하기 위한 익명 식별 토큰.
 *
 * <p>왜 필요한가 — 익명 댓글은 작성자를 특정할 방법이 없어서 <b>본인도 자기 댓글을
 * 지울 수 없었다.</b> 관리자만 삭제할 수 있으니, 오타 하나를 고치려 해도 방법이 없다.
 *
 * <p>닉네임으로는 대신할 수 없다. 닉네임은 자유 입력이라 다른 사람 것을 그대로 쓸 수 있고,
 * 그 경우 남의 댓글을 지울 수 있게 된다.
 *
 * <p>수집하는 정보가 없는 임의의 UUID 이며, 용도는 "같은 브라우저인지" 확인 하나뿐이다.
 * HttpOnly 로 두어 스크립트가 읽지 못하게 한다.
 */
public final class AnonymousIdentity {

    public static final String COOKIE_NAME = "md_anon";

    /** 1년. 브라우저를 닫아도 본인 댓글을 계속 관리할 수 있도록 충분히 길게 잡는다. */
    private static final int MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

    private AnonymousIdentity() {
    }

    /**
     * 현재 요청의 익명 토큰을 반환한다. 없으면 null.
     *
     * <p>조회만 한다. 토큰을 새로 만들지 않는다 — 단순 열람에도 쿠키를 심으면
     * 아무 행동도 하지 않은 방문자에게까지 식별자를 남기게 된다.
     */
    public static String current() {
        HttpServletRequest request = currentRequest();
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }

    /**
     * 익명 토큰을 가져오되, 없으면 새로 발급하고 응답에 쿠키를 실어준다.
     *
     * <p>댓글을 실제로 작성하는 시점에만 호출한다.
     */
    public static String getOrCreate(HttpServletResponse response) {
        String existing = current();
        if (existing != null) {
            return existing;
        }

        String issued = UUID.randomUUID().toString();

        Cookie cookie = new Cookie(COOKIE_NAME, issued);
        cookie.setHttpOnly(true);     // 스크립트가 읽지 못하게 한다(XSS 로 탈취 방지)
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Lax");

        // 운영은 HTTPS 이므로 보안 쿠키로 내보낸다.
        // 로컬(http) 에서는 Secure 쿠키가 저장되지 않으므로 요청 스킴을 따른다.
        HttpServletRequest request = currentRequest();
        cookie.setSecure(request != null && request.isSecure());

        response.addCookie(cookie);
        return issued;
    }

    private static HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
