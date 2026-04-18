package com.stockapp.stockserver.config;

import java.io.OutputStream;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class JsonpControllerAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public JsonpControllerAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  org.springframework.http.server.ServerHttpRequest request,
                                  org.springframework.http.server.ServerHttpResponse response) {

        if (request instanceof ServletServerHttpRequest servletRequest &&
            response instanceof ServletServerHttpResponse servletResponse) {

            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            HttpServletResponse httpResponse = servletResponse.getServletResponse();

            String callback = httpRequest.getParameter("callback");

            if (callback != null && !callback.isEmpty()) {
                try {
                    callback = callback.replaceAll("[^a-zA-Z0-9_]", ""); // 清理非法字元
                    String json = objectMapper.writeValueAsString(body);
                    String jsonp = callback + "(" + json + ");";

                    httpResponse.setContentType("application/javascript;charset=UTF-8");

                    OutputStream out = httpResponse.getOutputStream();
                    out.write(jsonp.getBytes("UTF-8"));
                    out.flush();

                    // 告訴 Spring：「我自己處理輸出了，不用再處理了」
                    return null;

                } catch (Exception e) {
                    throw new RuntimeException("JSONP 處理失敗", e);
                }
            }
        }

        // 沒有 callback，正常返回 JSON
        return body;
    }
}