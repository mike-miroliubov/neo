package org.mikemiroliubov.neo.client.impl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ClassLoaderUtils;
import org.mikemiroliubov.neo.client.request.HttpMethod;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class NeoClientImplTest {

    //public static final String URL = "http://localhost:8080/api/chats";
    public static final String POST_URL = "http://localhost:8080/api/chats";
    public static final String GET_URL = "http://worldtimeapi.org/api/timezone/America/Chicago";

    @Test
    void shouldGetTime() throws IOException, ExecutionException, InterruptedException {
        try (var client = new NeoClientImpl()) {
            var responseFuture = client.request(HttpMethod.GET, GET_URL);
            var response = responseFuture.get();
            System.out.println(new String(response.body().getData()));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.reason()).isEqualTo("OK");
        }
    }

    @Test
    void shouldGetFromUrl(WireMockRuntimeInfo wmRuntimeInfo) throws IOException, ExecutionException, InterruptedException, URISyntaxException {
        mockTimeApi(wmRuntimeInfo);

        try (var client = new NeoClientImpl()) {
            var futures = IntStream.range(0, 10)
                    .mapToObj(it -> client.request(HttpMethod.GET, wmRuntimeInfo.getHttpBaseUrl() + "/time"))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            for (var f : futures) {
                var response = f.get();
                System.out.println(new String(response.body().getData()));
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.reason()).isEqualTo("OK");
            }

        }
    }

    private void mockTimeApi(WireMockRuntimeInfo wmRuntimeInfo) throws IOException, URISyntaxException {
        var longJson = Files.readString(Path.of(ClassLoaderUtils.getClassLoader(this.getClass()).getResource("time-api.json").toURI()));
        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.register(get("/time").willReturn(okJson(longJson).withHeaders(new HttpHeaders(List.of(
                new HttpHeader("access-control-allow-credentials", "true"),
                new HttpHeader("access-control-allow-origin", "*"),
                new HttpHeader("access-control-expose-headers", ""),
                new HttpHeader("cache-control", "max-age=0, private, must-revalidate"),
                new HttpHeader("content-length", "422"),
                new HttpHeader("content-type", "application/json; charset=utf-8"),
                new HttpHeader("cross-origin-window-policy", "deny"),
                new HttpHeader("date", "Sun, 07 Sep 2025 02:00:09 GMT"),
                new HttpHeader("server", "Fly/93922353b (2025-09-05)"),
                new HttpHeader("vary", "accept-encoding"),
                new HttpHeader("x-content-type-options", "nosniff"),
                new HttpHeader("x-download-options", "noopen"),
                new HttpHeader("x-frame-options", "SAMEORIGIN"),
                new HttpHeader("x-permitted-cross-domain-policies", "none"),
                new HttpHeader("x-ratelimit-limit", "30"),
                new HttpHeader("x-ratelimit-remaining", "29"),
                new HttpHeader("x-ratelimit-reset", "1757214000"),
                new HttpHeader("x-request-from", "2601:197:500:1da0:64d9:b8bf:a899:3809"),
                new HttpHeader("x-request-id", "GGLdjrQC3B06sx7BGrhB"),
                new HttpHeader("x-request-regions", "a/bos;s/bos"),
                new HttpHeader("x-response-origin", "2875005c171618"),
                new HttpHeader("x-xss-protection", "1; mode=block"),
                new HttpHeader("via", "1.1 fly.io"),
                new HttpHeader("fly-request-id", "01K4GYDYM15XQS129MYA87C656-bos")
        )))));
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