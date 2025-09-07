package org.mikemiroliubov.neo.client;

import org.mikemiroliubov.neo.client.request.HttpMethod;
import org.mikemiroliubov.neo.client.response.Response;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface NeoClient extends AutoCloseable {
    CompletableFuture<Response> request(HttpMethod method, String url);
    CompletableFuture<Response> request(HttpMethod method, String url,
                                        Map<String, String> headers, String body);
}
