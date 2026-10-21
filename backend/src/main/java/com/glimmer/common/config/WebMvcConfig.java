package com.glimmer.common.config;

import com.glimmer.common.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器与 CORS。鉴权集中在 AuthInterceptor，Controller 不写登录注解。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CorsProperties corsProperties;

    public WebMvcConfig(AuthInterceptor authInterceptor, CorsProperties corsProperties) {
        this.authInterceptor = authInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsProperties.allowedOrigins().toArray(String[]::new);
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Trace-Id")
                .exposedHeaders("X-Trace-Id", "Retry-After", "X-RateLimit-Limit", "X-RateLimit-Remaining")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
