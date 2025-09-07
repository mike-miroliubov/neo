package org.mikemiroliubov.neo.client.impl;

import org.junit.jupiter.api.Test;
import org.mikemiroliubov.neo.client.request.HttpMethod;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class NeoClientImplTest {

    //public static final String URL = "http://localhost:8080/api/chats";
    public static final String POST_URL = "http://localhost:8080/api/chats";
    public static final String GET_URL = "http://worldtimeapi.org/api/timezone/America/Chicago";

    @Test
    void shouldGetFromUrl() throws IOException, ExecutionException, InterruptedException {
        try (var client = new NeoClientImpl()) {
            var responseFuture = client.request(HttpMethod.GET, GET_URL);
            var response = responseFuture.get();
            System.out.println(new String(response.body().getData()));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.reason()).isEqualTo("OK");
        }
    }

    @Test
    void shouldPostToUrl() throws IOException, ExecutionException, InterruptedException {
        try (var client = new NeoClientImpl()) {
            var requestBody = """
                    {
                        "id": "foo",
                        "from": "bar",
                        "text": "test",
                        "createdAt": "2025-09-07T00:39:14.164638"
                    }
                    """;

            var postResponse = client.request(HttpMethod.POST, POST_URL, Map.of("content-type", "application/json"), requestBody);
            System.out.println(new String(postResponse.get().body().getData()));
        }
    }
}