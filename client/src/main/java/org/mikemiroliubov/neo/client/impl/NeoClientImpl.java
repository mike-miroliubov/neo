package org.mikemiroliubov.neo.client.impl;

import org.mikemiroliubov.neo.client.NeoClient;
import org.mikemiroliubov.neo.client.impl.request.HttpRequestBuilder;
import org.mikemiroliubov.neo.client.request.Request;
import org.mikemiroliubov.neo.client.response.Response;
import org.mikemiroliubov.neo.client.response.ResponseBody;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class NeoClientImpl implements NeoClient {
    public static final String HTTP_HEADER_TERMINATOR = "\r\n\r\n";
    public static final String CONTENT_LENGTH_HEADER = "content-length";
    private final Selector selector;
    private final Thread dispatcherThread;

    public NeoClientImpl() throws IOException {
        selector = Selector.open();
        dispatcherThread = new Thread(new Dispatcher());
        dispatcherThread.start();
    }

    @Override
    public CompletableFuture<Response> get(String url, Request request) {
        return null;
    }

    @Override
    public CompletableFuture<Response> get(String url) throws IOException {
        var result = new CompletableFuture<Response>();
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

        var request = new HttpRequestBuilder("GET", path, uri.getHost(), uri.getPort(), null, result);

        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(uri.getHost(), port));

        channel.register(selector, SelectionKey.OP_CONNECT, request);
        selector.wakeup();

        return result;
    }

    @Override
    public void close() {
        selector.wakeup();
        dispatcherThread.interrupt();
    }

    private class Dispatcher implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    selector.select();

                    if (Thread.interrupted()) {
                        // will be interrupted on close
                        return;
                    }

                    var readyKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = readyKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        HttpRequestBuilder requestBuilder = (HttpRequestBuilder) key.attachment();
                        if (key.isConnectable()) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            if (sc.finishConnect()) {
                                System.out.println("Connected!");
                                key.interestOps(SelectionKey.OP_WRITE);
                            }
                        }
                        if (key.isWritable()) {
                            System.out.println("Ready to write!");
                            CompletableFuture.runAsync(() -> writeChannel((SocketChannel) key.channel(), requestBuilder));
                            key.interestOps(SelectionKey.OP_READ);
                        }
                        if (key.isReadable()) {
                            System.out.println("Ready to read!");
                            key.interestOps(0); // temporarily remove interest
                            CompletableFuture.runAsync(() -> readChannel(key, requestBuilder));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e); // TODO: log this properly
                }
            }
        }
    }

    private void readChannel(SelectionKey key, HttpRequestBuilder request) {
        SocketChannel channel = (SocketChannel) key.channel();
        var buf = ByteBuffer.allocate(1024);
        try {
            int read = channel.read(buf);
            System.out.println("Read " + read + " bytes");
            if (read > 0) {
                buf.flip();
                request.getResponseData().writeBytes(buf.array());

                if (!request.isResponseHeadersParsed()) {
                    parseResponseHeaders(request);
                }
                if (request.isResponseHeadersParsed()) {
                    if (request.getResponseData().size() >= request.getTotalResponseLength()) {
                        channel.close();
                        request.getResponseFuture().complete(new Response(new ResponseBody(request.getResponseData().toByteArray())));
                    }
                }

                key.interestOps(SelectionKey.OP_READ);
                selector.wakeup();
            }
            if (read == -1) {
                channel.close();
                request.getResponseFuture().complete(new Response(new ResponseBody(request.getResponseData().toByteArray())));
            }
        } catch (IOException e) {
            request.getResponseFuture().completeExceptionally(e);
        }
    }

    private void parseResponseHeaders(HttpRequestBuilder request) {
        var currentData = request.getResponseData().toString();
        int headerEndIndex = currentData.indexOf(HTTP_HEADER_TERMINATOR);
        if (headerEndIndex == -1) {
            return;
        }

        var headersStr = currentData.substring(0, headerEndIndex);
        var headerLines = headersStr.split("\r\n");
        var headers = Arrays.stream(headerLines)
                .map(l -> l.split(":"))
                .collect(Collectors.toMap(
                        it -> it[0].trim().toLowerCase(),
                        it -> it.length > 1 ? it[1].trim() : ""));

        request.setResponseHeaders(headers);
        request.setResponseHeadersParsed(true);

        if (headers.containsKey(CONTENT_LENGTH_HEADER)) {
            int bodyLength = Integer.parseInt(headers.get(CONTENT_LENGTH_HEADER));
            int totalLength = headersStr.getBytes().length + HTTP_HEADER_TERMINATOR.getBytes().length + bodyLength;
            request.setTotalResponseLength(totalLength);
            request.setExpectedBodyLength(bodyLength);
        }
    }

    private static void writeChannel(SocketChannel channel, HttpRequestBuilder requestBuilder) {
        try {
            channel.write(StandardCharsets.UTF_8.encode(requestBuilder.buildHttpRequest()));
        } catch (IOException e) {
            requestBuilder.getResponseFuture().completeExceptionally(e);
        }
    }
}
