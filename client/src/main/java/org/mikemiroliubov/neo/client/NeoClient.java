package org.mikemiroliubov.neo.client;

import org.mikemiroliubov.neo.client.request.Request;
import org.mikemiroliubov.neo.client.response.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface NeoClient extends AutoCloseable {
    CompletableFuture<Response> get(String url, Request request);
    CompletableFuture<Response> get(String url) throws IOException;
}
