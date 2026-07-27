package com.ott.common.security;

import com.ott.common.security.filter.JwtAuthenticationFilter;
import com.ott.common.security.handler.JwtAccessDeniedHandler;
import com.ott.common.security.handler.JwtAuthenticationEntryPoint;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.security.util.CookieUtil;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        JwtTokenProvider.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        CookieUtil.class
})
public class CommonSecurityConfiguration {
}
