package com.moondap.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 1. HTTP -> HTTPS 전역 리다이렉트 (X-Forwarded-Proto 기반)
 * 2. kckoo.co.kr 도메인 -> moondap.com 영구 리다이렉트
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomainRedirectFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(DomainRedirectFilter.class);

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (!"prod".equals(activeProfile)) {
            chain.doFilter(request, response);
            return;
        }

        String serverName = req.getServerName();
        String proto = req.getHeader("X-Forwarded-Proto");
        String requestURI = req.getRequestURI();
        String queryString = req.getQueryString();

        boolean isKckoo = serverName.endsWith("kckoo.co.kr");
        boolean isHttp = "http".equalsIgnoreCase(proto);

        // HTTP 접속이거나 kckoo 도메인인 경우 HTTPS moondap.com으로 리다이렉트
        if (isHttp || isKckoo) {
            StringBuilder redirectUrl = new StringBuilder("https://moondap.com");
            redirectUrl.append(requestURI);
            
            if (queryString != null) {
                redirectUrl.append("?").append(queryString);
            }

            log.info("Redirecting to HTTPS: {} -> {}", serverName, redirectUrl);
            
            res.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            res.setHeader("Location", redirectUrl.toString());
            res.setHeader("Connection", "close");
            return;
        }

        chain.doFilter(request, response);
    }
}
