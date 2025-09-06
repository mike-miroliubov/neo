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
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

public class NeoClientImpl implements NeoClient {
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

        var request = new HttpRequestBuilder("GET", path, uri.getHost(), null, result);

        try (SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE, request);

            channel.connect(new InetSocketAddress(uri.getHost(), port));
        }

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

                        HttpRequestBuilder requestBuilder = (HttpRequestBuilder) key.attachment();
                        if (key.isConnectable()) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            if (sc.finishConnect()) {
                                System.out.println("Connected!");
                            }
                        }
                        if (key.isWritable()) {
                            CompletableFuture.runAsync(() -> writeChannel((SocketChannel) key.channel(), requestBuilder));
                        }
                        if (key.isReadable()) {
                            CompletableFuture.runAsync(() -> readChannel((SocketChannel) key.channel(), requestBuilder.getResponseFuture()));
                        }

                        iterator.remove();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e); // TODO: log this properly
                }
            }
        }
    }

    private static void readChannel(SocketChannel channel, CompletableFuture<Response> future) {
        var buf = ByteBuffer.allocate(1024);
        try {
            channel.read(buf);
            buf.flip();

            // TODO: smart reading to account for variable length
            future.complete(new Response(new ResponseBody(buf.array())));
        } catch (IOException e) {
            future.completeExceptionally(e);
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
