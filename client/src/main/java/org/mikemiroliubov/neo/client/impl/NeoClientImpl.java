package org.mikemiroliubov.neo.client.impl;

import org.mikemiroliubov.neo.client.NeoClient;
import org.mikemiroliubov.neo.client.request.HttpMethod;
import org.mikemiroliubov.neo.client.response.Response;

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
    private static final String HTTP_HEADER_TERMINATOR = "\r\n\r\n";
    private static final String CONTENT_LENGTH_HEADER = "content-length";
    private static final String PROTOCOL_HEADER = "HTTP/1.1";
    private static final int BUFFER_SIZE = 1024;

    private final Selector selector;
    private final Thread dispatcherThread;

    public NeoClientImpl() throws IOException {
        selector = Selector.open();
        dispatcherThread = new Thread(this::eventLoop);
        dispatcherThread.start();
    }

    @Override
    public CompletableFuture<Response> request(HttpMethod method, String url) {
        var result = new CompletableFuture<Response>();

        try {
            scheduleRequest(new HttpContext(method, url, result));
        } catch (IOException e) {
            result.completeExceptionally(e);
        }

        return result;
    }

    @Override
    public CompletableFuture<Response> request(HttpMethod method, String url,
                                               Map<String, String> headers, String body) {
        var result = new CompletableFuture<Response>();

        try {
            scheduleRequest(new HttpContext(method, url, body, headers, result));
        } catch (IOException e) {
            result.completeExceptionally(e);
        }

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

    private void readChannel(SelectionKey key, HttpContext context) {
        SocketChannel channel = (SocketChannel) key.channel();
        var buf = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            int read = channel.read(buf);
            System.out.println("Read " + read + " bytes");

            if (read > 0) {
                buf.flip();
                // Sometimes less data is available then the buf size, but it doesn't mean that its done, just stalling.
                // If we copy the full buffer like in context.getResponseData().write(buf.array()), we'll read in empty
                // space. Not only this is incorrect, it also screws up our assumption in
                // `context.getResponseData().size() >= context.getTotalResponseLength()` because the responseData is
                // inflated with empty space and we stop reading prematurely.
                // So we must only read the remaining bytes, not the whole buffer.
                context.getResponseData().write(buf.array(), 0, buf.remaining());

                if (!context.isResponseHeadersParsed()) {
                    parseResponseHeaders(context);
                }
                if (context.isResponseHeadersParsed() && context.getResponseData().size() >= context.getTotalResponseLength()) {
                    // if we read all expected data, based on content length, close the channel and notify
                    // the caller that we're done
                    channel.close();
                    context.complete();
                    return;
                }

                // otherwise, subscribe to read more
                key.interestOps(SelectionKey.OP_READ);
                selector.wakeup();
            }

            if (read == -1) {  // there's nothing more to read
                System.out.println("No more data to read!");
                channel.close();
                context.complete();
            }
        } catch (IOException e) {
            tryCloseChannel(channel);

            context.getResponseFuture().completeExceptionally(e);
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

    private void parseResponseHeaders(HttpContext context) {
        var currentData = context.getResponseData().toString();
        int headerEndIndex = currentData.indexOf(HTTP_HEADER_TERMINATOR); // TODO: search in byte array instead
        if (headerEndIndex == -1) {
            return;
        }

        var headersStr = currentData.substring(0, headerEndIndex);
        var headerLines = headersStr.split("\r\n");
        var headers = Arrays.stream(headerLines)
                .map(l -> l.split(":", 2))
                .collect(Collectors.toMap(
                        it -> it[0].trim().toLowerCase(),
                        it -> it.length > 1 ? it[1].trim() : ""));

        context.setResponseHeaders(headers);
        context.setResponseHeadersParsed(true);

        var codeAndReason = headerLines[0].substring(PROTOCOL_HEADER.length() + 1).split(" ", 2);
        context.setStatusCode(Integer.parseInt(codeAndReason[0]));
        context.setStatusReason(codeAndReason[1]);


        if (headers.containsKey(CONTENT_LENGTH_HEADER)) {
            int bodyLength = Integer.parseInt(headers.get(CONTENT_LENGTH_HEADER));
            int totalLength = headersStr.getBytes().length + HTTP_HEADER_TERMINATOR.getBytes().length + bodyLength;
            context.setTotalResponseLength(totalLength);
            context.setExpectedBodyLength(bodyLength);
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
