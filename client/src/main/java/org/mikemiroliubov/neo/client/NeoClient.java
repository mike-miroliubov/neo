package org.mikemiroliubov.neo.client;

import org.mikemiroliubov.neo.client.request.HttpMethod;
import org.mikemiroliubov.neo.client.response.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface NeoClient extends AutoCloseable {
    CompletableFuture<Response> request(HttpMethod method, String url) throws IOException;
}
