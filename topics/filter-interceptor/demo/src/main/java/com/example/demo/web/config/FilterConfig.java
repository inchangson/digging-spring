package com.example.demo.web.config;

import com.example.demo.web.filter.CsrfFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean csrfFilter() {
        FilterRegistrationBean<Filter> csrfRegBean = new FilterRegistrationBean<>();

        csrfRegBean.setFilter(new CsrfFilter());
        csrfRegBean.setOrder(1);
        csrfRegBean.addUrlPatterns("/*");

        return csrfRegBean;
    }
}
