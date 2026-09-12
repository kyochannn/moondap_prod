package com.moondap.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * 서블릿 컨테이너가 /error 로 포워드한 요청을 처리한다.
 *
 * <p>ControllerAdvice 가 잡지 못하는 경로(필터 단계에서 발생한 오류 등)가 여기로 온다.
 * 뷰 이름만 돌려주면 상태 코드가 200 으로 바뀌므로 ModelAndView 에 상태를 명시한다.
 */
@Slf4j
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status != null) {
            try {
                HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(status.toString()));
                if (resolved != null) {
                    httpStatus = resolved;
                }
            } catch (NumberFormatException e) {
                log.warn("에러 상태 코드를 해석할 수 없습니다: {}", status);
            }
        }

        log.info("에러 페이지 진입: status={}, uri={}",
                httpStatus.value(), request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        // 전용 페이지가 있는 상태 코드만 해당 뷰로, 나머지는 500 으로 보낸다.
        // 어떤 경우에도 원래 상태 코드는 유지한다(이전에는 메인으로 302 시켜 오류를 감췄다).
        String view = switch (httpStatus) {
            case NOT_FOUND -> "error/404";
            case FORBIDDEN -> "error/403";
            case UNAUTHORIZED -> "error/401";
            default -> "error/500";
        };

        ModelAndView mav = new ModelAndView(view);
        mav.setStatus(httpStatus);
        return mav;
    }
}
