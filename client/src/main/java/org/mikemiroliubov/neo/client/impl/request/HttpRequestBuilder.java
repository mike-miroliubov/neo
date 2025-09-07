package org.mikemiroliubov.neo.client.impl.request;

import lombok.Data;
import lombok.Value;
import org.mikemiroliubov.neo.client.response.Response;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Build HTTP requests in the format of:
 *
 * GET /path HTTP/1.1
 * Host: example.com
 * Connection: close
 */
@Data
public class HttpRequestBuilder {
    private final String method;
    private final String path;
    private final String host;
    private final int port;
    private final Map<String, String> requestHeaders = new LinkedHashMap<>();
    private final String body;

    private final CompletableFuture<Response> responseFuture;
    private final ByteArrayOutputStream responseData = new ByteArrayOutputStream();
    private boolean responseHeadersParsed = false;
    private Map<String, String> responseHeaders = new HashMap<>();
    private int expectedBodyLength = -1;
    private int totalResponseLength = -1;

    public String buildHttpRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(host);
        if (port != -1) {
            sb.append(":").append(port);
        }
        sb.append("\r\n");
        //sb.append("Connection: close\r\n");

        requestHeaders.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
        if (body != null && !body.isEmpty()) {
            sb.append("Content-Length: ").append(body.length()).append("\r\n");
        }

        sb.append("\r\n");

        if (body != null) {
            sb.append(body);
        }

        return sb.toString();
    }
}
