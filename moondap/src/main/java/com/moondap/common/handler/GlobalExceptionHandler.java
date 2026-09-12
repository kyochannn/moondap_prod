package com.moondap.common.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.moondap.common.exception.UserMessageException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 처리기.
 *
 * <p>두 가지를 책임진다.
 *
 * <ol>
 *   <li><b>올바른 상태 코드</b> — 이전에는 뷰 이름만 반환해서 500·404 페이지가
 *       HTTP 200 으로 나갔다. 검색엔진이 오류 페이지를 정상 문서로 색인하고,
 *       AJAX 호출부는 실패를 감지하지 못했다.</li>
 *   <li><b>메시지 노출 범위</b> — 이전에는 {@code ex.getMessage()} 를 그대로 내보냈다.
 *       MyBatis/JDBC 예외 메시지에는 SQL 문과 테이블 이름이 들어 있어 그대로 노출됐다.
 *       이제 {@link UserMessageException} 의 메시지만 전달하고 나머지는 일반 문구로 바꾼다.
 *       원문은 로그에만 남는다.</li>
 * </ol>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String GENERIC_MESSAGE = "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    /**
     * 사용자에게 보여줄 메시지를 담은 예외.
     * 입력값 문제이므로 400 으로 응답한다.
     */
    @ExceptionHandler(UserMessageException.class)
    public Object handleUserMessage(UserMessageException ex, HttpServletRequest request) {
        // 사용자 입력 문제는 서버 장애가 아니므로 스택트레이스까지 남기지 않는다.
        log.warn("사용자 오류: {} (uri={})", ex.getMessage(), request.getRequestURI());

        if (isAjax(request)) {
            return jsonError(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return errorView("error/500", HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 요청 DTO 의 Bean Validation 실패.
     *
     * <p>필드에 붙인 message 를 그대로 사용자에게 전달한다. 여러 건이어도 첫 번째만
     * 보여준다 — 화면이 alert 하나로 처리하기 때문에 나열해도 읽히지 않는다.
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object handleValidation(BindException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("입력값을 확인해 주세요.");

        log.warn("입력값 검증 실패: {} (uri={})", message, request.getRequestURI());

        if (isAjax(request)) {
            return jsonError(HttpStatus.BAD_REQUEST, message);
        }
        return errorView("error/500", HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 요청 본문을 읽을 수 없는 경우.
     *
     * <p>JSON 문법 오류, 타입 불일치(예: Integer 필드에 "abc") 등이다.
     * 클라이언트가 잘못 보낸 것이므로 500 이 아니라 400 으로 응답한다.
     * 원문에는 파싱 위치와 필드 경로가 담기므로 사용자에게 노출하지 않는다.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public Object handleMalformedRequest(Exception ex, HttpServletRequest request) {
        log.warn("요청 형식 오류: uri={}, cause={}", request.getRequestURI(), ex.getMessage());

        String message = "요청 형식이 올바르지 않습니다.";
        if (isAjax(request)) {
            return jsonError(HttpStatus.BAD_REQUEST, message);
        }
        return errorView("error/500", HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 권한 없음. 로그인은 되어 있으나 권한이 모자란 경우다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("접근 거부: uri={}", request.getRequestURI());

        if (isAjax(request)) {
            return jsonError(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }
        return errorView("error/403", HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    /**
     * 그 밖의 모든 예외. 내부 오류로 간주하고 메시지를 감춘다.
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외: uri={}", request.getRequestURI(), ex);

        if (isAjax(request)) {
            return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_MESSAGE);
        }
        return errorView("error/500", HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_MESSAGE);
    }

    /**
     * 매핑되지 않은 URL.
     * (spring.mvc.throw-exception-if-no-handler-found=true 설정이 있어야 동작한다)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handle404(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("404: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return notFound(request);
    }

    /**
     * 정적 리소스를 찾지 못한 경우.
     * 브라우저가 자동으로 요청하는 경로(.well-known 등)라 로그 수준을 낮춘다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("정적 리소스 없음: {}", ex.getResourcePath());
        return notFound(request);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────

    private Object notFound(HttpServletRequest request) {
        if (isAjax(request)) {
            return jsonError(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
        }
        return errorView("error/404", HttpStatus.NOT_FOUND, "요청한 페이지를 찾을 수 없습니다.");
    }

    /**
     * AJAX 요청 여부.
     *
     * jQuery 는 X-Requested-With 를 자동으로 붙이지만, contentType 을 application/json
     * 으로 지정한 경우 Accept 헤더에 json 이 없을 수 있어 두 가지를 모두 확인한다.
     */
    private boolean isAjax(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        String contentType = request.getContentType();

        return "XMLHttpRequest".equals(requestedWith)
                || (accept != null && accept.contains("application/json"))
                || (contentType != null && contentType.contains("application/json"));
    }

    private ResponseEntity<Map<String, Object>> jsonError(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 오류 화면.
     *
     * <p>error/500 은 500 전용이 아니라 400 응답에서도 쓴다. 화면에 상태 코드가
     * "500" 으로 고정돼 있으면 실제로는 잘못된 입력인데 서버 장애처럼 보이므로
     * 실제 코드를 모델에 넣어 템플릿이 그대로 출력하게 한다.
     */
    private ModelAndView errorView(String viewName, HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView(viewName);
        mav.setStatus(status);
        mav.addObject("errorCode", status.value());
        mav.addObject("errorMessage", message);
        return mav;
    }
}
