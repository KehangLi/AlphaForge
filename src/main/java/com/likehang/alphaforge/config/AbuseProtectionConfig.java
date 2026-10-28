package com.likehang.alphaforge.config;

import com.likehang.alphaforge.security.limits.ClientIpProperties;
import com.likehang.alphaforge.security.limits.ClientIpResolver;
import com.likehang.alphaforge.security.limits.RateLimitFilter;
import com.likehang.alphaforge.security.limits.RateLimitProperties;
import com.likehang.alphaforge.security.limits.RateLimitService;
import com.likehang.alphaforge.service.quota.GuestQuotaProperties;
import com.likehang.alphaforge.service.quota.UploadProperties;
import io.github.bucket4j.TimeMeter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({RateLimitProperties.class, ClientIpProperties.class,
        GuestQuotaProperties.class, UploadProperties.class}) // allow other components to inject and use it
public class AbuseProtectionConfig {

    // will automatically inject to RateLimitService
    @Bean
    public TimeMeter rateLimitTimeMeter() {
        return TimeMeter.SYSTEM_NANOTIME;
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitService limits, ClientIpResolver clientIpResolver,
                                         ObjectMapper objectMapper) {
        return new RateLimitFilter(limits, clientIpResolver, objectMapper);
    }

    //Disable automatic registration of this filter and let Spring Security’s filter chain manage it
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
