package com.example.aichinese.controller;

import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Spring MVC sets 416 and Content-Range on an unsatisfiable range but still hands the converter the entire file. */
@ControllerAdvice
final class StoredFileRangeAdvice implements ResponseBodyAdvice<Resource> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ResourceHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    @Nullable
    public Resource beforeBodyWrite(@Nullable Resource body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servlet
                && servlet.getServletResponse().getStatus() == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            return null;
        }
        return body;
    }
}
