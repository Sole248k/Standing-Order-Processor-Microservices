package com.ewb.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Enumeration;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GatewayProxyController {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyController.class);

    private final String standingOrderUrl;
    private final String executionUrl;
    private final String paymentUrl;
    private final String notificationUrl;
    private final RestClient restClient;

    public GatewayProxyController(
            @Value("${services.standing-order.url:http://localhost:8081}") String standingOrderUrl,
            @Value("${services.execution.url:http://localhost:8082}") String executionUrl,
            @Value("${services.payment.url:http://localhost:8083}") String paymentUrl,
            @Value("${services.notification.url:http://localhost:8084}") String notificationUrl) {
        this.standingOrderUrl = standingOrderUrl;
        this.executionUrl = executionUrl;
        this.paymentUrl = paymentUrl;
        this.notificationUrl = notificationUrl;
        this.restClient = RestClient.builder().build();
    }

    @RequestMapping(value = "/**")
    public ResponseEntity<byte[]> proxy(
            HttpMethod method,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        String path = request.getRequestURI().substring("/api".length());
        String targetBaseUrl = determineTargetService(path);

        if (targetBaseUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("No route configured for path: " + path).getBytes());
        }

        String targetUrl = targetBaseUrl + path;
        if (request.getQueryString() != null) {
            targetUrl += "?" + request.getQueryString();
        }

        log.debug("Proxying {} {} -> {}", method, request.getRequestURI(), targetUrl);

        try {
            var reqSpec = restClient.method(method).uri(URI.create(targetUrl));

            // Forward headers
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!headerName.equalsIgnoreCase("host") && !headerName.equalsIgnoreCase("content-length")) {
                    reqSpec.header(headerName, request.getHeader(headerName));
                }
            }

            if (body != null && body.length > 0) {
                reqSpec.body(body);
            }

            ResponseEntity<byte[]> response = reqSpec.retrieve().toEntity(byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_JSON)
                    .body(response.getBody());

        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsByteArray());
        } catch (Exception ex) {
            log.error("Proxy error calling {}: {}", targetUrl, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"success\":false,\"message\":\"Gateway error: " + ex.getMessage() + "\"}").getBytes());
        }
    }

    private String determineTargetService(String path) {
        if (path.startsWith("/standing-orders")) {
            return standingOrderUrl;
        } else if (path.startsWith("/executions")) {
            return executionUrl;
        } else if (path.startsWith("/transfers") || path.startsWith("/accounts")) {
            return paymentUrl;
        } else if (path.startsWith("/notifications")) {
            return notificationUrl;
        }
        return null;
    }
}
