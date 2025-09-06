package org.mikemiroliubov.neo.client.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class NeoClientImplTest {

    @Test
    void shouldGetFromUrl() throws IOException, ExecutionException, InterruptedException {
        try (var client = new NeoClientImpl()) {
            var responseFuture = client.get("http://worldtimeapi.org/api/timezone/America/Chicago");
            var response = responseFuture.get();
            System.out.println(response.getBody().getData());
        }

    }
}