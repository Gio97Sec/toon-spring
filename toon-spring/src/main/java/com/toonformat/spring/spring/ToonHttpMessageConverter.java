package com.toonformat.spring.spring;

import com.toonformat.spring.ToonMapper;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Spring MVC {@link org.springframework.http.converter.HttpMessageConverter}
 * that reads and writes TOON ({@code text/toon}) payloads.
 * <p>
 * Register it in your Spring configuration or rely on
 * {@link ToonAutoConfiguration} for automatic setup.
 *
 * <pre>{@code
 * @Configuration
 * public class WebConfig implements WebMvcConfigurer {
 *     @Override
 *     public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
 *         converters.add(new ToonHttpMessageConverter());
 *     }
 * }
 * }</pre>
 */
public class ToonHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    public static final MediaType MEDIA_TYPE_TOON = new MediaType("text", "toon", StandardCharsets.UTF_8);

    private final ToonMapper mapper;

    public ToonHttpMessageConverter() {
        this(new ToonMapper());
    }

    public ToonHttpMessageConverter(ToonMapper mapper) {
        super(StandardCharsets.UTF_8, MEDIA_TYPE_TOON);
        this.mapper = mapper;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true; // supports any class
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        String body = new String(inputMessage.getBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            return mapper.readValue(body, clazz);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException("Failed to parse TOON: " + e.getMessage(), e, inputMessage);
        }
    }

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        String toon = mapper.writeValueAsString(object);
        OutputStream out = outputMessage.getBody();
        out.write(toon.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
