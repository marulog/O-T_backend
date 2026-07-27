package com.ott.common.web;

import com.ott.common.web.config.SwaggerConfig;
import com.ott.common.web.config.WebMvcConfig;
import com.ott.common.web.exception.GlobalExceptionHandler;
import com.ott.common.web.response.PageResponseMapper;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        WebMvcConfig.class,
        SwaggerConfig.class,
        GlobalExceptionHandler.class,
        PageResponseMapper.class
})
public class CommonWebConfiguration {
}
