package org.mikemiroliubov.neo.client.impl;

import org.mikemiroliubov.neo.client.NeoClient;
import org.mikemiroliubov.neo.client.impl.request.HttpContext;
import org.mikemiroliubov.neo.client.request.HttpMethod;
import org.mikemiroliubov.neo.client.response.Response;
import org.mikemiroliubov.neo.client.response.ResponseBody;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class NeoClientImpl implements NeoClient {
    public static final String HTTP_HEADER_TERMINATOR = "\r\n\r\n";
    public static final String CONTENT_LENGTH_HEADER = "content-length";
    private final Selector selector;
    private final Thread dispatcherThread;

    public NeoClientImpl() throws IOException {
        selector = Selector.open();
        dispatcherThread = new Thread(this::eventLoop);
        dispatcherThread.start();
    }

    @Override
    public CompletableFuture<Response> request(HttpMethod method, String url) throws IOException {
        var result = new CompletableFuture<Response>();
        scheduleRequest(new HttpContext(method, url, result));
        return result;
    }

    @Override
    public CompletableFuture<Response> request(HttpMethod method, String url, Map<String, String> headers, String body) throws IOException {
        var result = new CompletableFuture<Response>();
        scheduleRequest(new HttpContext(method, url, body, headers, result));
        return result;
    }

    private void scheduleRequest(HttpContext context) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(context.getSocketAddress());

        channel.register(selector, SelectionKey.OP_CONNECT, context);
        selector.wakeup();
    }

    @Override
    public void close() {
        selector.wakeup();
        dispatcherThread.interrupt();
    }

    public void eventLoop() {
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

                    HttpContext context = (HttpContext) key.attachment();
                    if (key.isConnectable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        if (sc.finishConnect()) {
                            System.out.println("Connected!");
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                    if (key.isWritable()) {
                        System.out.println("Ready to write!");
                        key.interestOps(0); // temporarily remove interest
                        CompletableFuture.runAsync(() -> writeChannel(key, context));
                    }
                    if (key.isReadable()) {
                        System.out.println("Ready to read!");
                        key.interestOps(0); // temporarily remove interest
                        CompletableFuture.runAsync(() -> readChannel(key, context));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e); // TODO: log this properly
            }
        }
    }

    private void readChannel(SelectionKey key, HttpContext request) {
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
            tryCloseChannel(channel);

            request.getResponseFuture().completeExceptionally(e);
        }
    }

    private static void tryCloseChannel(SocketChannel channel) {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace(); // TODO: proper logging
        }
    }

    private void parseResponseHeaders(HttpContext request) {
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

    private void writeChannel(SelectionKey key, HttpContext context) {
        var channel = (SocketChannel) key.channel();
        try {
            channel.write(context.getRequestBody());

            if (context.getRequestBody().hasRemaining()) {
                // still has smth to write
                key.interestOps(SelectionKey.OP_WRITE);
            } else {
                // we wrote everything, can switch to reading now
                key.interestOps(SelectionKey.OP_READ);
            }
            selector.wakeup();
        } catch (IOException e) {
            tryCloseChannel(channel);
            context.getResponseFuture().completeExceptionally(e);
        }
    }
}
