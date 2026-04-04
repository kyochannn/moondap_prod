package com.moondap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Value("${file.upload-dir}")
    private String uploadDir;

	@Value("${file.profile-dir}")
	private String profileDir;
	
	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
		
		// 일반 파일 업로드 경로 매핑
		registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir);
        
		// 프로필 이미지 전용 경로 매핑
		registry.addResourceHandler("/profile/**")
				.addResourceLocations("file:" + profileDir);

        System.out.println("설정된 업로드 경로: " + uploadDir);
        System.out.println("설정된 프로필 경로: " + profileDir);
    }
}
