package com.example.demo.web.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class TimeMeasureInterceptor implements HandlerInterceptor {
    public static final String API_TIME = "apiTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long currentTime = System.currentTimeMillis();
        request.setAttribute(API_TIME, currentTime);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        String requestURI = request.getRequestURI();
        long currentTime = System.currentTimeMillis();
        long preHandleTime = (long) request.getAttribute(API_TIME);
        long processTime = currentTime - preHandleTime;

        log.info("afterCompletion: api [{}] -> [{}] ms", requestURI, processTime);
    }
}
