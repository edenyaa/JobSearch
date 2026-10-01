package com.ats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final AuthInterceptor authInterceptor;
	private final String[] allowedOrigins;

	public WebConfig(AuthInterceptor authInterceptor,
			@Value("${cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
		this.authInterceptor = authInterceptor;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("Authorization", "Content-Type")
				.exposedHeaders("Content-Disposition")
				.maxAge(3600);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
	}

}
