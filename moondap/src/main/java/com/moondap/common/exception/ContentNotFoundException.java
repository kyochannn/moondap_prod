package com.moondap.common.exception;

/**
 * 요청한 콘텐츠가 존재하지 않을 때 던진다. 응답은 404 다.
 *
 * <p>{@link UserMessageException}(400) 과 나누는 이유는 검색엔진 때문이다.
 * 이전에는 없는 테스트 주소가 메인으로 302 리다이렉트됐고, 밸런스 게임 쪽은 400 을 냈다.
 * 둘 다 크롤러에게는 "이 URL 은 살아 있다"로 읽힌다(soft 404). 그러면 삭제한 콘텐츠의
 * 주소가 색인에 계속 남고, 그 URL 로 들어온 방문자는 자기가 누른 것과 다른 화면을 본다.
 *
 * <p>메시지는 사용자에게 그대로 노출되므로 내부 정보를 담지 않는다.
 */
public class ContentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContentNotFoundException(String message) {
        super(message);
    }
}
