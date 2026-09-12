package com.moondap.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.multipart.support.MultipartFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.moondap.config.auth.CustomAuthFailureHandler;
import com.moondap.config.auth.NotFoundAwareAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthFailureHandler customAuthFailureHandler;

    @Bean
    BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 로그인 중인 세션 목록. 관리자가 사용자를 정지·삭제할 때 해당 세션을 끊는 데 쓴다.
     *
     * <p>PrincipalDetails.isEnabled() 는 로그인 시점에만 평가되므로, 이것이 없으면
     * 정지된 사용자가 기존 세션으로 계속 서비스를 이용할 수 있다.
     */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * 세션 생성·소멸 이벤트를 시큐리티에 전달한다.
     * 이것이 없으면 SessionRegistry 에 죽은 세션이 계속 쌓인다.
     */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * multipart 요청의 CSRF 토큰을 읽기 위해 MultipartFilter 를 시큐리티 필터보다 앞에 둔다.
     *
     * CsrfFilter 는 request.getParameter("_csrf") 로 토큰을 찾는데, multipart 본문은
     * 기본적으로 DispatcherServlet 단계에서야 파싱된다. 즉 시큐리티 필터 시점에는
     * 파라미터가 비어 있어 파일 업로드 폼이 전부 403 으로 막힌다.
     * (해당되는 폼: /joinProc, /mypage/edit)
     *
     * 토큰을 쿼리스트링에 실어 우회하는 방법도 있으나, 그 경우 토큰이 접근 로그와
     * Referer 헤더에 남으므로 채택하지 않았다.
     */
    @Bean
    FilterRegistrationBean<MultipartFilter> multipartFilterRegistration() {
        FilterRegistrationBean<MultipartFilter> registration =
                new FilterRegistrationBean<>(new MultipartFilter());
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider)
            throws Exception {

        http.authorizeHttpRequests(auth -> auth

            // ── 1. 정적 리소스 / 업로드 파일 ──────────────────────────
            .requestMatchers("/assets/**", "/uploads/**", "/profile/**").permitAll()
            .requestMatchers("/favicon.png", "/apple-touch-icon.png",
                             "/robots.txt", "/sitemap.xml", "/ads.txt",
                             "/.well-known/**").permitAll()
            .requestMatchers("/error").permitAll()
            // 버튼 디자인 가이드. 개발용 정적 문서라 데이터를 담지 않는다.
            // 검색 노출은 페이지의 noindex 와 robots.txt 로 막는다.
            .requestMatchers("/testBtn.html").permitAll()

            // ── 2. 보호 영역 ────────────────────────────────────────
            // 반드시 공개 규칙(4번)보다 먼저 선언한다. 시큐리티는 먼저 매칭되는 규칙이
            // 이기므로, /test/** 가 앞서면 /test/manage/** 가 공개로 새어나간다.
            .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
            .requestMatchers("/test/manage/**", "/balanceGame/manage/**").hasAnyRole("ADMIN", "USER")
            .requestMatchers("/mypage", "/mypage/**").authenticated()

            // 밸런스 게임 쓰기 작업은 로그인 필수.
            // 서비스 계층의 CheckMyTest() 소유권 검사와 이중으로 방어한다.
            .requestMatchers("/balanceGame/insertBalanceGameView",
                             "/balanceGame/insertBalanceGame",
                             "/balanceGame/updateBalanceGameView",
                             "/balanceGame/updateBalanceGame",
                             "/balanceGame/deleteBalanceGame").authenticated()
            // 댓글 삭제는 관리자 전용이 아니다. 작성자 본인(로그인 계정 또는 익명 토큰)도
            // 지울 수 있어야 하므로, 권한 판정은 서비스(canDeleteComment)가 한다.
            // 여기서 hasRole('ADMIN') 으로 막으면 작성자가 URL 단계에서 먼저 차단된다.

            // ── 3. 인증 / 가입 ──────────────────────────────────────
            .requestMatchers("/loginView", "/loginProc", "/logout").permitAll()
            .requestMatchers("/joinSelectView", "/joinView", "/joinProc",
                             "/joinCompleteView", "/joinViewAfterError").permitAll()
            .requestMatchers("/checkUsername", "/checkNickname", "/checkAdminKey").permitAll()

            // ── 4. 공개 콘텐츠 ──────────────────────────────────────
            .requestMatchers("/", "/privacy", "/terms", "/partnership").permitAll()
            .requestMatchers("/test/**", "/balanceGame/**", "/egenTeto/**").permitAll()

            // ── 5. 기본 거부 ────────────────────────────────────────
            // 이전에는 anyRequest().permitAll() 이어서 새 컨트롤러를 추가할 때마다
            // 명시적으로 막지 않으면 전부 공개로 열렸다. 기본값을 뒤집는다.
            .anyRequest().authenticated()
        );

        // 기본 거부 정책의 부작용 보정.
        // 규칙에 걸리지 않는 주소(오타·죽은 링크)까지 "인증 필요"로 취급돼 로그인 화면으로
        // 넘어갔다. 매핑된 컨트롤러가 없을 때만 404 로 답한다.
        http.exceptionHandling(ex -> ex.authenticationEntryPoint(
                new NotFoundAwareAuthenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/loginView"),
                        handlerMappingProvider)
        ));

        http.formLogin(form -> form
            .loginPage("/loginView")
            .loginProcessingUrl("/loginProc")
            .defaultSuccessUrl("/", true)
            .failureHandler(customAuthFailureHandler)
            .permitAll()
        );

        // CSRF 가 켜지면 로그아웃은 POST 여야 한다. 헤더의 로그아웃 링크도
        // 토큰을 담은 POST 폼으로 교체했다. (fragment/header.html)
        http.logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID")
            .permitAll()
        );

        // 세션을 레지스트리에 등록해 관리자가 강제 만료시킬 수 있게 한다.
        // maximumSessions(-1): 동시 접속 수는 제한하지 않는다. 목적은 추적뿐이다.
        http.sessionManagement(session -> session
            .maximumSessions(-1)
            .sessionRegistry(sessionRegistry())
            .expiredUrl("/loginView?expired=true")
        );

        // CSRF 활성화. 토큰은 세션에 보관하고 다음 두 경로로 전달한다.
        //   - 일반 폼  : th:action 을 쓰면 Thymeleaf 가 hidden 필드를 자동 주입
        //   - AJAX     : fragment/link.html 의 meta 태그 → fragment/script.html 의 전역 설정
        http.csrf(csrf -> csrf
            .csrfTokenRepository(new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository())
        );

        return http.build();
    }

}
