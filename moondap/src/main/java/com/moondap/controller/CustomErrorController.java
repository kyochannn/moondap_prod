package com.moondap.controller;

import com.moondap.dto.MdContentItemDTO;
import com.moondap.service.MdTestUserService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        log.info("Error occurred. Status: {}", status);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            // 404 (Not Found) 에러인 경우 전용 404 페이지 표시
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "error/404";
            }
            
            // 500 (Internal Server Error) 에러인 경우 전용 500 페이지 표시
            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                return "error/500";
            }
        }
        
        // 그 외 에러는 메인으로 리다이렉트
        return "redirect:/";
    }
}
