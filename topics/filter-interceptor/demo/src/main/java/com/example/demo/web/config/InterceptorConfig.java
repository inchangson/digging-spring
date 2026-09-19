package com.example.demo.web.config;

import com.example.demo.web.interceptor.TimeMeasureInterceptor;
import com.example.demo.web.interceptor.LoggingInterceptor;
import com.example.demo.web.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private static final String[] whitelist = {"/", "/space", "/space/members/add", "/space/login", "/space/logout", "/space/info", "/css/*", "/*.ico", "/error"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoggingInterceptor())
                .order(1)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/*.ico", "/error");

        registry.addInterceptor(new LoginInterceptor())
                .order(2)
                .addPathPatterns("/**")
                .excludePathPatterns(whitelist);

        registry.addInterceptor(new TimeMeasureInterceptor())
                .order(3)
                .addPathPatterns("/**");
    }
}
