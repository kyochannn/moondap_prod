package com.moondap.config.auth;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인이 필요한 요청은 로그인 화면으로 보내되, 애초에 존재하지 않는 주소는 404 로 답한다.
 *
 * <p>시큐리티 기본값이 {@code anyRequest().authenticated()} 라, 어떤 규칙에도 걸리지 않는
 * 주소 — 즉 오타나 죽은 링크 — 도 "인증이 필요한 요청"으로 취급돼 로그인 화면으로 넘어갔다.
 * 방문자는 없는 페이지를 눌렀을 뿐인데 로그인을 요구받는 것처럼 보이고, 크롤러는 404 대신
 * 200(로그인 화면)을 받아 없는 URL 을 계속 살아 있는 페이지로 인식한다.
 *
 * <p>기본 거부 정책은 그대로 둔 채, 매핑된 핸들러가 하나도 없을 때만 404 로 바꾼다.
 * 보호 대상 주소는 여전히 로그인 화면으로 가므로 "그 주소가 존재하는가"는 새어나가지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class NotFoundAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** 실제로 인증이 필요한 경우의 처리(로그인 화면으로 이동). */
    private final AuthenticationEntryPoint delegate;

    /**
     * 지연 조회한다. 시큐리티 설정이 WebMvc 설정을 직접 의존하면 순환 참조가 생긴다.
     */
    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        if (isUnmapped(request)) {
            log.debug("매핑되지 않은 주소: {} {}", request.getMethod(), request.getRequestURI());
            // sendError 는 컨테이너의 오류 처리로 넘긴다. 스프링 부트가 error/404 템플릿을 렌더링한다.
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }

        delegate.commence(request, response, authException);
    }

    /**
     * 이 요청을 처리할 컨트롤러가 없는지 확인한다.
     *
     * <p>정적 리소스는 확인 대상이 아니다. 공개해야 할 정적 파일은 모두 시큐리티에서
     * permitAll 로 열려 있어 여기까지 오지 않는다.
     *
     * <p>판단할 수 없으면 false 를 돌려 기존 동작(로그인 화면)을 유지한다.
     * 존재하는 보호 자원을 404 로 답하는 쪽이 그 반대보다 나쁘기 때문이다.
     */
    private boolean isUnmapped(HttpServletRequest request) {
        RequestMappingHandlerMapping mapping = handlerMappingProvider.getIfAvailable();
        if (mapping == null) {
            return false;
        }
        try {
            return mapping.getHandler(request) == null;
        } catch (Exception e) {
            // 경로는 맞지만 메서드가 다른 경우(405) 등. 주소 자체는 존재하므로 404 가 아니다.
            return false;
        }
    }
}
