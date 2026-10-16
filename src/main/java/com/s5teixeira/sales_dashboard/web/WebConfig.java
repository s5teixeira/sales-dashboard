package com.s5teixeira.sales_dashboard.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

/**
 * Tells the browser never to cache pages or API responses. After a dataset is loaded or deleted,
 * the next request always goes back to the server (and therefore to the database), so an old
 * dataset can't reappear from the browser cache or the back button.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        WebContentInterceptor noCache = new WebContentInterceptor();
        noCache.addCacheMapping(CacheControl.noStore(), "/", "/transactions", "/api/**");
        registry.addInterceptor(noCache);
    }
}
