package com.moondap.common.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 처리기 (Global Exception Handler)
 * 애플리케이션 전역에서 발생하는 예외를 한 곳에서 처리하여 적절한 에러 페이지로 리다이렉트합니다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 모든 일반적인 예외 발생 시 호출됩니다.
     * 500 Internal Server Error 처리
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex, Model model, HttpServletRequest request) {
        log.error("Global Exception: ", ex);

        // AJAX 요청인지 확인
        // jQuery는 X-Requested-With: XMLHttpRequest 헤더를 자동으로 설정함
        // contentType: 'application/json' 설정 시에는 Accept 헤더에 application/json이 없을 수 있으므로 두 가지 모두 체크
        String acceptHeader = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");
        boolean isAjax = (acceptHeader != null && acceptHeader.contains("application/json"))
                      || "XMLHttpRequest".equals(xRequestedWith);

        if (isAjax) {
            // AJAX 요청의 경우 에러 메시지를 담은 JSON 응답 반환
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "Internal Server Error");
            errorMap.put("message", ex.getMessage());
            errorMap.put("status", 500);
            return ResponseEntity.status(500).body(errorMap);
        }

        // 일반 요청의 경우 에러 페이지로 리다이렉트
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/500";
    }

    /**
     * 404 Not Found 에러 처리
     * 존재하지 않는 URL 요청 시 호출됩니다.
     * (application.properties에 spring.mvc.throw-exception-if-no-handler-found=true
     * 설정 필요)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public String handle404(NoHandlerFoundException ex, Model model) {
        log.error("404 Error: URL={}, Method={}", ex.getRequestURL(), ex.getHttpMethod());
        return "error/404";
    }

    /**
     * 정적 리소스를 찾지 못할 때 발생하는 예외 처리
     * 브라우저가 자동으로 요청하는 .well-known 등의 경로가 없을 때 로그 노이즈를 줄이기 위함입니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Resource Not Found: URL={}", ex.getResourcePath());
        return "error/404";
    }
}
