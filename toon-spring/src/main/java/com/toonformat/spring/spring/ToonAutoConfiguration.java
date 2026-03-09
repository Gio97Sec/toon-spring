package com.toonformat.spring.spring;

import com.toonformat.spring.ToonMapper;
import com.toonformat.spring.ToonOptions;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration that registers a {@link ToonMapper} bean
 * and the {@link ToonHttpMessageConverter} for {@code text/toon} content type.
 * <p>
 * Activated automatically when Spring Boot and Spring Web are on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(ToonMapper.class)
public class ToonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToonOptions toonOptions() {
        return new ToonOptions();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToonMapper toonMapper(ToonOptions options) {
        return new ToonMapper(options);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    public ToonHttpMessageConverter toonHttpMessageConverter(ToonMapper mapper) {
        return new ToonHttpMessageConverter(mapper);
    }
}
