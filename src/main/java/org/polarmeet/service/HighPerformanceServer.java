package org.polarmeet.service;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;
import io.grpc.stub.StreamObserver;
import org.polarmeet.proto.HighPerformanceServiceGrpc;
import org.polarmeet.proto.Request;
import org.polarmeet.proto.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

// Record to hold request data for batch processing
record RequestData(String data) {}

public class HighPerformanceServer {
    private final Server server;
    private static final int PORT = 9090;
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public HighPerformanceServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(2);
        EventLoopGroup workerGroup = new NioEventLoopGroup(32);

        NettyServerBuilder builder = NettyServerBuilder.forPort(PORT)
                .channelType(NioServerSocketChannel.class)
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
                .executor(executor)
                .maxConcurrentCallsPerConnection(100000)
                .maxInboundMessageSize(1024 * 1024)
                .maxInboundMetadataSize(1024 * 64)
                .addService(new HighPerformanceServiceImpl());

        this.server = builder.build();
    }

    private static class HighPerformanceServiceImpl extends HighPerformanceServiceGrpc.HighPerformanceServiceImplBase {
        private final Response okResponse = Response.newBuilder()
                .setStatus("OK")
                .build();

        // Queue for batch processing
        private final BlockingQueue<RequestData> requestQueue = new LinkedBlockingQueue<>();

        public HighPerformanceServiceImpl() {
            // Start the batch processing thread
            executor.submit(this::processBatches);
        }

        private void processBatches() {
            List<RequestData> batch = new ArrayList<>();

            while (true) {
                try {
                    // Collect up to 1000 requests or wait 100ms
                    long deadline = System.currentTimeMillis() + 100;
                    while (batch.size() < 1000 && System.currentTimeMillis() < deadline) {
                        RequestData request = requestQueue.poll(
                                deadline - System.currentTimeMillis(),
                                TimeUnit.MILLISECONDS
                        );
                        if (request != null) {
                            batch.add(request);
                        }
                    }

                    // Process the batch if we have any requests
                    if (!batch.isEmpty()) {
                        DatabaseConfig.batchInsert(batch);
                        batch.clear();
                    }
                } catch (Exception e) {
                    System.err.println("Error processing batch: " + e.getMessage());
                }
            }
        }

        @Override
        public void process(Request request, StreamObserver<Response> responseObserver) {
            // Immediately respond to the client
            responseObserver.onNext(okResponse);
            responseObserver.onCompleted();

            // Queue the request for batch processing
            requestQueue.offer(new RequestData(request.getData()));
        }
    }

    public void start() throws IOException {
        server.start();
        System.out.println("Server started on port " + PORT);
    }

    public void blockUntilShutdown() throws InterruptedException {
        server.awaitTermination();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        HighPerformanceServer server = new HighPerformanceServer();
        server.start();
        server.blockUntilShutdown();
    }
}
