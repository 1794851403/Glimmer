package com.glimmer.common.config;

import com.glimmer.common.auth.AuthInterceptor;
import com.glimmer.common.storage.StorageProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 拦截器与 CORS。鉴权集中在 AuthInterceptor，Controller 不写登录注解。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String DEFAULT_LOCAL_ROOT = "./uploads";

    private final AuthInterceptor authInterceptor;
    private final CorsProperties corsProperties;
    private final StorageProperties storageProperties;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        CorsProperties corsProperties,
                        StorageProperties storageProperties) {
        this.authInterceptor = authInterceptor;
        this.corsProperties = corsProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // OSS 模式不需要暴露本地目录；local 模式把 /uploads/** 映射到配置的根目录。
        if (storageProperties.isOss()) {
            return;
        }
        StorageProperties.Local local = storageProperties.local();
        String rootDir = local == null || local.rootDir() == null || local.rootDir().isBlank()
                ? DEFAULT_LOCAL_ROOT : local.rootDir();
        String location = Paths.get(rootDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
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
