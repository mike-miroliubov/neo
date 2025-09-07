package org.mikemiroliubov.neo.client.impl;

import org.junit.jupiter.api.Test;
import org.mikemiroliubov.neo.client.request.HttpMethod;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

class NeoClientImplTest {

    //public static final String URL = "http://localhost:8080/api/chats";
    public static final String URL = "http://worldtimeapi.org/api/timezone/America/Chicago";

    @Test
    void shouldRequestFromUrl() throws IOException, ExecutionException, InterruptedException {
        try (var client = new NeoClientImpl()) {
            var responseFuture = client.request(HttpMethod.GET, URL);
            var response = responseFuture.get();
            System.out.println(new String(response.getBody().getData()));
        }

    }
}