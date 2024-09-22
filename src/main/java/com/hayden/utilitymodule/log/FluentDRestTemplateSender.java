package com.hayden.utilitymodule.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.fluentd.logger.errorhandler.ErrorHandler;
import org.fluentd.logger.sender.Sender;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;


/**
 * Note VM Argument Required:
 * -Dfluentd.logger.sender.class=com.hayden.utilitymodule.log.FluentDRestTemplateSender
 */
@RequiredArgsConstructor
public class FluentDRestTemplateSender implements Sender {

    RestTemplate restTemplate = new RestTemplateBuilder().build();

    private final String host;
    private final int port;
    private final int timeout;
    private final int bufferCapacity;

    private ErrorHandler handler;

    @Override
    public boolean emit(String s, Map<String, Object> map) {
        try {
            var o = new ObjectMapper().writeValueAsString(map);
            HttpHeaders headers = new HttpHeaders();
            headers.put("Content-Type", Lists.newArrayList(MediaType.APPLICATION_JSON_VALUE));
            var h = new HttpEntity<>(o, headers);
            var posted = restTemplate.postForEntity("http://localhost:8888", h, String.class);
            return parseResponseToDidSucceed(posted);
        } catch (IOException e) {
            handler.handleNetworkError(e);
            System.out.printf("FAIL in the networking for the logging. How do you log about a log? %s%n", e.getMessage());
            return false;
        }
    }

    private static boolean parseResponseToDidSucceed(ResponseEntity<String> posted) {
        if (!posted.getStatusCode().is2xxSuccessful()) {
            System.out.printf("Call to fluentbit was not successful %s%n", posted.getStatusCode());
        }
        return posted.getStatusCode().is2xxSuccessful();
    }

    @Override
    public boolean emit(String s, long l, Map<String, Object> map) {
        return this.emit(s, map);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public String getName() {
        return "Logback FluentD Logger Sender";
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.handler = errorHandler;
    }

    @Override
    public void removeErrorHandler() {
    }
}
