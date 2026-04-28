package com.moondap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@lombok.RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    BCryptPasswordEncoder bCryptPasswordEncoder() {

	    return new BCryptPasswordEncoder();
	}

    private final com.moondap.config.auth.CustomAuthFailureHandler customAuthFailureHandler;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests((auth) -> auth
        	.requestMatchers("/", "/loginView", "/joinView", "/joinSelectView", "/joinProc", "/joinCompleteView", "/joinViewAfterError", "/assets/**", "/privacy", "/terms").permitAll()
        	.requestMatchers("/checkUsername", "/checkNickname", "/checkAdminKey").permitAll()
        	.requestMatchers("/uploads/**").permitAll()
            .requestMatchers("/test/manage/**", "/balanceGame/manage/**").hasAnyRole("ADMIN", "USER")
            .requestMatchers("/balanceGame/**").permitAll()
            .requestMatchers("/egenTeto/**").permitAll()
        	.requestMatchers("/profile/**").permitAll()
        	.requestMatchers("/.well-known/**").permitAll()
            .requestMatchers("/test/manage/**", "/balanceGame/manage/**").hasAnyRole("ADMIN", "USER")
            .requestMatchers("/test/**").permitAll()
            .requestMatchers("/balanceGame/**").permitAll()
            .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
//            .requestMatchers("/my/**").hasAnyRole("ADMIN", "USER")
            .anyRequest().permitAll()
        );

        http.formLogin((auth) -> auth.loginPage("/loginView")
            .loginProcessingUrl("/loginProc")
            .defaultSuccessUrl("/", true)
            .failureHandler(customAuthFailureHandler)
            .permitAll()
        );

        http.logout((auth) -> auth.logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .permitAll()
        );

        // csrf 작동 방지를 위해 (개발용)
        http.csrf((auth) -> auth.disable());
        
        return http.build();
    }
    
}
