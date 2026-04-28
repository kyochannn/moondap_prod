package com.moondap.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * kckoo.co.kr 도메인으로 유입되는 요청을 moondap.com으로 리다이렉트하는 필터
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 모든 보안 필터보다 먼저 실행되어야 함
public class DomainRedirectFilter implements Filter {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String serverName = req.getServerName();

        // 운영 환경(prod)에서 kckoo.co.kr 도메인으로 접근한 경우 리다이렉트
        if ("prod".equals(activeProfile) && (serverName.endsWith("kckoo.co.kr"))) {
            String requestURI = req.getRequestURI();
            String queryString = req.getQueryString();
            
            // 새 주소 구성 (https 적용 권장)
            StringBuilder redirectUrl = new StringBuilder("https://moondap.com");
            redirectUrl.append(requestURI);
            
            if (queryString != null) {
                redirectUrl.append("?").append(queryString);
            }

            res.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY); // 301 Redirect
            res.setHeader("Location", redirectUrl.toString());
            res.setHeader("Connection", "close");
            return;
        }

        chain.doFilter(request, response);
    }
}
