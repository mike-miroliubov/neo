package org.mikemiroliubov.neo.client.impl;

import lombok.Data;
import org.mikemiroliubov.neo.client.request.HttpMethod;
import org.mikemiroliubov.neo.client.response.Response;
import org.mikemiroliubov.neo.client.response.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A helper class that stores everything we need to send an HTTP request and parse a response
 * <p>
 *
 * <pre>
 * GET /path HTTP/1.1
 * Host: example.com
 * Connection: close
 * </pre>
 */
@Data
public class HttpContext {
    // request data
    private final HttpMethod method;
    private final String path;
    private final String host;
    private final int port;
    private final Map<String, String> requestHeaders;
    private final String body;
    private final InetSocketAddress socketAddress;
    private final ByteBuffer requestBody;

    // response data
    private final CompletableFuture<Response> responseFuture;
    private final ByteArrayOutputStream responseData = new ByteArrayOutputStream();
    private Map<String, String> responseHeaders = new HashMap<>();
    private int statusCode = -1;
    private String statusReason;
    private boolean responseHeadersParsed = false;
    private int expectedBodyLength = -1;
    private int totalResponseLength = -1;

    public HttpContext(HttpMethod method, String url, CompletableFuture<Response> result) {
        this(method, url, null, null, result);
    }

    public HttpContext(HttpMethod method, String url, String body, Map<String, String> headers, CompletableFuture<Response> result) {
        var uri = URI.create(url);

        int port = uri.getPort();
        if (port == -1) {
            // Default based on scheme
            port = uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
        }

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "";
        }
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }

        this.port = uri.getPort();
        this.method = method;
        this.host = uri.getHost();
        this.body = body;
        this.path = path;
        socketAddress = new InetSocketAddress(uri.getHost(), port);

        responseFuture = result;
        requestHeaders = headers != null ? headers : Map.of();

        requestBody = StandardCharsets.UTF_8.encode(buildHttpRequest());
    }

    public void complete() {
        byte[] bodyData = Arrays.copyOfRange(
                responseData.toByteArray(),
                totalResponseLength - expectedBodyLength,
                responseData.size());

        responseFuture.complete(
                new Response(
                        new ResponseBody(bodyData),
                        responseHeaders,
                        200,
                        statusReason
                ));
    }

    private String buildHttpRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append(method.name()).append(" ").append(path).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(host);
        if (port != -1) {
            sb.append(":").append(port);
        }
        sb.append("\r\n");

        requestHeaders.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
        if (body != null && !body.isEmpty()) {
            sb.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
        }

        sb.append("\r\n");

        if (body != null) {
            sb.append(body);
        }

        return sb.toString();
    }
}
