package com.moondap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Value("${file.upload-dir}")
    private String uploadDir;
	
	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
		
		// 파일 업로드 시 해당 경로로 저장 됨.
		// 설정 경로는 application.properties에 저장되어 있음.
		registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir);
        
        System.out.println("설정된 리소스 경로: " + uploadDir);
    }
}
