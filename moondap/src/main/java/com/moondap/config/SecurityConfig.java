package com.moondap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    BCryptPasswordEncoder bCryptPasswordEncoder() {

	    return new BCryptPasswordEncoder();
	}

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests((auth) -> auth
        	.requestMatchers("/", "/loginView", "/joinView", "/assets/**").permitAll()
        	.requestMatchers("/uploads/**").permitAll()
        	.requestMatchers("/balanceGame/**").permitAll()
            .requestMatchers("/admin").hasRole("ADMIN")
//            .requestMatchers("/my/**").hasAnyRole("ADMIN", "USER")
            .anyRequest().authenticated()
        );

        http.formLogin((auth) -> auth.loginPage("/loginView")
            .loginProcessingUrl("/loginProc")
            .permitAll()
        );

        // csrf 작동 방지를 위해 (개발용)
        http.csrf((auth) -> auth.disable());
        
        return http.build();
    }
    
}
