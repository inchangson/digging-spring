package com.example.demo.web.filter;

import com.example.demo.web.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class CsrfFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestURI = httpRequest.getRequestURI();
        HttpMethod method = HttpMethod.resolve(httpRequest.getMethod());

        try {
            log.info("doFilter() START: REQUEST [{}][{}]", requestURI, method);

            // GET 요청 아닐 시 referrer 검증
            if (!HttpMethod.GET.equals(method)) {
                String prevUrl = httpRequest.getHeader("REFERER");

                if ((prevUrl == null) || (!prevUrl.matches(Constant.csrfRegex))) {
                    log.info("doFilter(): connection failed" + prevUrl);
                    httpResponse.sendError(400);
                    return;
                }
            }

            log.info("doFilter(): csrf check passed");
            chain.doFilter(request, response);
        } catch (Exception e) {
            throw e;
        } finally {
            log.info("doFilter() END: RESPONSE [{}][{}]", requestURI, method);
        }
    }
}
