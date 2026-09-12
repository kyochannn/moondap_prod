package com.moondap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${file.upload-dir}")
	private String uploadDir;

	@Value("${file.profile-dir}")
	private String profileDir;

	@Override
	public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {

		// 일반 파일 업로드 경로 매핑
		registry.addResourceHandler("/uploads/**")
				.addResourceLocations("file:" + uploadDir);

		// 프로필 이미지 전용 경로 매핑
		registry.addResourceHandler("/profile/**")
				.addResourceLocations("file:" + profileDir);

		log.info("설정된 업로드 경로: {}", uploadDir);
		log.info("설정된 프로필 경로: {}", profileDir);
	}

	/**
	 * 업로드 파일 응답에 보안 헤더를 붙인다.
	 *
	 * <p>FileService 가 신규 업로드의 실제 내용을 검사하도록 바뀌었지만, 검증이 느슨하던
	 * 시절 저장된 파일이 이미 디스크에 남아 있다(예: contents 디렉터리의 pdf).
	 * 그 파일들이 브라우저에서 문서로 해석되지 않도록 응답 단에서도 막는다.
	 *
	 * <ul>
	 *   <li>nosniff : 확장자와 다른 내용을 브라우저가 추측해 실행하는 것을 차단</li>
	 *   <li>CSP sandbox : 직접 URL 로 접근해 문서로 열리더라도 스크립트 실행을 금지.
	 *       &lt;img&gt; 로 불러오는 경우 CSP 는 문서에만 적용되므로 렌더링에는 영향이 없다.</li>
	 * </ul>
	 */
	@Override
	public void addInterceptors(@NonNull InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(@NonNull HttpServletRequest request,
									 @NonNull HttpServletResponse response,
									 @NonNull Object handler) {
				response.setHeader("X-Content-Type-Options", "nosniff");
				response.setHeader("Content-Security-Policy", "sandbox");
				return true;
			}
		}).addPathPatterns("/uploads/**", "/profile/**");
	}
}
