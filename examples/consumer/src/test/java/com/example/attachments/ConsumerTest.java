package com.example.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.alex59.filestreams.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ConsumerTest {
    @Test
    void copiedControllerUsesPublishedArtifactsAndEnforcesAccountBeforeStorage() {
        try (var app = new SpringApplicationBuilder(AttachmentApplication.class, Fixture.class).run(
                "--server.port=0", "--file-streams.s3.bucket=test", "--file-streams.s3.region=test",
                "--file-streams.limits.max-object-size=16B")) {
            var client = WebTestClient.bindToServer()
                    .baseUrl("http://127.0.0.1:" + app.getEnvironment().getProperty("local.server.port")).build();
            URI object = URI.create("http://127.0.0.1:" + app.getEnvironment().getProperty("local.server.port")
                    + "/attachments/alice%3Ainvoice.txt");
            var store = app.getBean(FixtureStorage.class);
            client.put().uri(object).bodyValue("invoice").exchange().expectStatus().isUnauthorized();
            client.put().uri(object).headers(headers -> headers.setBasicAuth("bob", "fixture-password")).bodyValue("invoice")
                    .exchange().expectStatus().isForbidden();
            assertThat(store.uploads.get()).isZero();
            client.put().uri(object).headers(headers -> headers.setBasicAuth("alice", "fixture-password")).header("X-Upload-Length", "7")
                    .bodyValue("invoice").exchange().expectStatus().isCreated().expectBody(String.class)
                    .value(body -> assertThat(body).contains("\"committed\":true", "alice:invoice.txt", "\"bytes\":7"));
            client.get().uri(object).headers(headers -> headers.setBasicAuth("alice", "fixture-password")).exchange().expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("invoice");
            client.get().uri(object).headers(headers -> headers.setBasicAuth("alice", "fixture-password")).header("Range", "bytes=0-2")
                    .exchange().expectStatus().isEqualTo(206).expectBody(String.class).isEqualTo("inv");
            client.put().uri(object).headers(headers -> headers.setBasicAuth("alice", "fixture-password")).header("X-Upload-Length", "18")
                    .bodyValue("too large document").exchange().expectStatus().isEqualTo(413);
            client.get().uri(URI.create(object.toString().replace("/attachments/", "/objects/"))).headers(headers -> headers.setBasicAuth("alice", "fixture-password")).exchange().expectStatus().isNotFound();
            assertThat(store.closed.get()).isEqualTo(2);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Fixture {
        @Bean SecurityWebFilterChain testSecurity(ServerHttpSecurity http) {
            // EN: Retain the real default firewall; only test identity/CSRF are synthetic here.
            // RU: Сохраняем настоящий стандартный firewall; только identity/CSRF тестовые.
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(Customizer.withDefaults())
                    .authorizeExchange(routes -> routes.anyExchange().authenticated()).build();
        }
        @Bean FixtureStorage objects(StreamLimits limits) { return new FixtureStorage(limits); }
        @Bean MapReactiveUserDetailsService testUsers() {
            // EN: Public test credentials only; production retains its existing identity provider.
            // RU: Только публичные тестовые credentials; production сохраняет своего identity provider.
            return new MapReactiveUserDetailsService(
                    User.withUsername("alice").password("{noop}fixture-password").roles("USER").build(),
                    User.withUsername("bob").password("{noop}fixture-password").roles("USER").build());
        }
    }

    static final class FixtureStorage implements ObjectStreams {
        final ConcurrentHashMap<String, byte[]> stored = new ConcurrentHashMap<>();
        final AtomicInteger uploads = new AtomicInteger();
        final AtomicInteger closed = new AtomicInteger();
        final StreamLimits limits;
        FixtureStorage(StreamLimits limits) { this.limits = limits; }
        public Mono<UploadResult> upload(UploadRequest request, Publisher<ByteBuffer> body) {
            uploads.incrementAndGet();
            // EN: Only this tiny bounded test fixture aggregates; production uses the S3 adapter.
            // RU: Только этот малый ограниченный fixture накапливает данные; production использует S3 adapter.
            return new CheckedStream(body, request.length(), limits).content()
                    .collect(ByteArrayOutputStream::new, (out, buffer) -> {
                        byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); out.writeBytes(bytes);
                    }).map(out -> {
                        byte[] bytes = out.toByteArray(); stored.put(request.key(), bytes);
                        return new UploadResult(true, request.key(), bytes.length, "opaque-test-etag");
                    });
        }
        public Mono<ObjectDownload> download(String key, String header) {
            byte[] bytes = stored.get(key);
            ByteRange range = header == null ? null : ByteRange.parse(header, bytes.length);
            int start = range == null ? 0 : (int) range.start();
            int length = range == null ? bytes.length : (int) range.length();
            return Mono.just(new ObjectDownload(length, "text/plain", "opaque-test-etag", range,
                    Flux.just(ByteBuffer.wrap(bytes, start, length).slice()), closed::incrementAndGet));
        }
    }
}
