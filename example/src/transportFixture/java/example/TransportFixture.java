package example;

import io.github.alex59.filestreams.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// EN: This test-only launcher exercises actual Boot/TLS/HTTP2 and WebFlux, without claiming S3 coverage.
// RU: Тестовый запуск проверяет настоящий Boot/TLS/HTTP2 и WebFlux, но не работу S3.
public final class TransportFixture {
    public static void main(String[] args) {
        SpringApplication.run(FixtureConfiguration.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FileStreamsApplication.class)
    static class FixtureConfiguration {
        @Bean
        ObjectStreams fixtureStorage() { return new MemoryStorage(); }
    }

    private record Stored(byte[] bytes, String type, String etag) {}

    private static final class MemoryStorage implements ObjectStreams {
        private static final long TOTAL_LIMIT = 32L * 1024 * 1024;
        private static final StreamLimits LIMITS = new StreamLimits(16L * 1024 * 1024, 1024 * 1024);
        private final Map<String, Stored> objects = new HashMap<>();
        private final Semaphore uploads = new Semaphore(2);
        private long storedBytes;

        @Override
        public Mono<UploadResult> upload(UploadRequest request, Publisher<ByteBuffer> body) {
            return Mono.defer(() -> {
                var checked = new CheckedStream(body, request.length(), LIMITS);
                if (!uploads.tryAcquire()) {
                    return Mono.error(new StreamFailure(StreamFailure.Code.BUSY, "Test fixture upload limit reached"));
                }
                // EN: Aggregation is confined to this bounded test fixture; production uses the S3 stream engine.
                // RU: Накопление данных ограничено тестовым fixture; production использует потоковый S3 engine.
                return checked.content().collect(ByteArrayOutputStream::new, (output, buffer) -> {
                    byte[] chunk = new byte[buffer.remaining()];
                    buffer.get(chunk);
                    output.writeBytes(chunk);
                }).map(output -> commit(request, output.toByteArray()))
                        .doFinally(signal -> uploads.release());
            });
        }

        private synchronized UploadResult commit(UploadRequest request, byte[] bytes) {
            Stored previous = objects.get(request.key());
            long nextSize = storedBytes - (previous == null ? 0 : previous.bytes().length) + bytes.length;
            if (nextSize > TOTAL_LIMIT || (previous == null && objects.size() >= 16)) {
                throw new StreamFailure(StreamFailure.Code.TOO_LARGE, "Test fixture storage limit reached; restart fixture");
            }
            String etag = UUID.randomUUID().toString();
            objects.put(request.key(), new Stored(bytes, request.contentType(), etag));
            storedBytes = nextSize;
            return new UploadResult(true, request.key(), bytes.length, etag);
        }

        @Override
        public Mono<ObjectDownload> download(String key, String rangeHeader) {
            return Mono.defer(() -> {
                Stored stored;
                synchronized (this) { stored = objects.get(key); }
                if (stored == null) {
                    return Mono.error(new ObjectUnavailable(ObjectUnavailable.Reason.NOT_FOUND,
                            new IllegalArgumentException("No such fixture object")));
                }
                ByteRange range = rangeHeader == null ? null : ByteRange.parse(rangeHeader, stored.bytes().length);
                int start = range == null ? 0 : (int) range.start();
                int length = range == null ? stored.bytes().length : (int) range.length();
                var body = Flux.just(ByteBuffer.wrap(stored.bytes(), start, length).slice());
                return Mono.just(new ObjectDownload(length, stored.type(), stored.etag(), range, body, () -> {}));
            });
        }
    }
}
