# 0.1.0 — Spring Boot file streams

[← Versions](../index.md) · [README](../../README.md) · [RU](ru.md)

[Build](#1-build-locally) · [S3](#2-stream-to-s3) · [HTTP](#3-browser-to-java-contract) · [Overrides](#4-reuse-application-beans) · [Run](#5-run-the-paired-browser-and-backend-example) · [Tests and limits](#6-verification-and-boundaries)

[Complete API, options, errors and troubleshooting](reference-en.md)

## 1. Build locally

Check out `file-streams` and this repository next to each other. Use JDK 17+; the build targets Java 17. The base projects retain the same API on 21/25/27, without preview features. Node 20+ is needed for browser examples; OpenSSL creates local TLS certificates.

From this repository:

```bash
../file-streams/gradlew -p ../file-streams build publishToMavenLocal \
  -Dmaven.repo.local="$PWD/../file-streams/build/local-repository"
./gradlew build :example:installDist
./gradlew publishToMavenLocal
```

A consumer resolves the starter from Maven Local and the base artifacts from the isolated directory. Replace the relative path with your own checkout location:

```kotlin
repositories {
    mavenLocal()
    maven { url = uri("../file-streams/build/local-repository") }
    mavenCentral()
}
dependencies {
    implementation("io.github.59alex:file-streams-spring-boot-starter:0.1.0")
}
```

The starter includes Spring Boot WebFlux and the S3/WebFlux modules. It does not publish artifacts to a registry when you run these commands. [Build definition](../../build.gradle.kts).

## 2. Stream to S3

**Task:** upload documents without keeping the entire object in application memory or creating a separate multipart-session API. The library already checks source length, applies backpressure, bounds AWS buffers and distinguishes an SDK commit from bytes read.

Add the starter, use a normal `@SpringBootApplication`, and set:

```yaml
file-streams:
  enabled: true
  s3:
    bucket: application-files
    region: eu-west-1
  limits:
    max-object-size: 512MB
    max-buffer-size: 1MB
    max-concurrent-transfers: 2
  http:
    enabled: true
```

No controller or S3-client bean is needed. [Complete application](../../example/src/main/java/example/FileStreamsApplication.java) · [complete configuration](../../example/src/main/resources/application.yml) · [auto-configuration implementation](../../src/main/java/io/github/alex59/filestreams/boot/FileStreamsAutoConfiguration.java).

| Setting | Default | Meaning |
| --- | --- | --- |
| `file-streams.enabled` | `false` | Explicitly create integration beans. |
| `s3.bucket` | unset | Required when creating `ObjectStreams`. |
| `s3.region` | unset | Required when creating the AWS client. |
| `s3.endpoint` | unset | AWS endpoint by default; optional HTTP(S) compatible-storage URL. |
| `limits.max-object-size` | `16GB` | Maximum declared/actual upload length; not a download-size cap. |
| `limits.max-buffer-size` | `1MB` | Maximum individual upload source/network buffer. |
| `limits.max-concurrent-transfers` | `2` | Shared upload/download admission slots; excess requests fail with `BUSY`. |
| `http.enabled` | `false` | Expose `/objects/**` in a reactive web application. |

Size suffixes follow Spring `DataSize`: `1MB` is 1,048,576 bytes. Limits are validated at startup; the existing S3 profile also rejects sizes above its 10,000 internal 5-MiB part bound. AWS credentials use the standard SDK chain. The starter contains no access-key configuration. The context closes resources it creates. An active download retains its admission slot until its body completes, fails, is cancelled or is closed.

## 3. Browser to Java contract

**Task:** upload a browser File, show source-read progress, then show confirmation only after storage commit. The matching TypeScript uses `@59alex/file-streams`:

```ts
import { uploadStream, downloadTo, objectUrl } from '@59alex/file-streams';
const url = objectUrl('https://localhost:19443/objects', key);
const stop = new AbortController();
const committed = await uploadStream({
  url, key, source: file.stream(), knownLength: file.size,
  maxBytes: 512 * 1024 * 1024, signal: stop.signal,
});
await downloadTo({ url, destination, maxBytes: 512 * 1024 * 1024, signal: stop.signal });
```

`file`, `key` and `destination` come from the application; a supported file picker can provide a writable destination. Neither operation needs a whole-file Blob buffer. The [React companion](https://github.com/dlorian59/file-streams-react) supplies components/hooks around this API; its repository is currently prepared locally.

On the server the existing adapter calls `ObjectStreams.upload(request, requestBody)` and `ObjectStreams.download(key, rangeHeader)`. The starter does not duplicate those algorithms or JSON serialization.

| Request | Response / behavior |
| --- | --- |
| `PUT /objects/{encodeURIComponent(key)}` with raw bytes | `201 application/json`: `{ "committed": true, "key": "report.bin", "bytes": 123, "etag": "opaque" }`. |
| Optional `X-Upload-Length` | Validated against actual bytes; disagreement with Content-Length is rejected. |
| `GET` same path | `200`, Content-Length, ETag and Accept-Ranges: bytes; one streamed response body. |
| `GET` with one `Range` | `206` with checked Content-Range; invalid ranges produce `416`. |
| Client/source error | Structured error with `committed: false` when the rejection is known. |
| Storage/finalization uncertainty | `committed: null`; reconcile the object before deciding whether to retry. |

A source-read progress event does not mean that S3 has committed. Abort cannot roll back an object already committed during finalization. Streams are one-shot; no automatic replay is attempted. ETags are opaque, not checksums. Downloads may have already written part of the destination when cancelled.

Local subscription cancellation and source errors are distinct from HTTP termination. If a transport presents termination as normal EOF for an unknown-length request, a backend can accept the received prefix; a browser abort therefore remains an unknown outcome, even before the intended source ends. Supply `knownLength` / `X-Upload-Length` whenever the final size is known. This detects a short body but does not make cancellation a rollback guarantee.

**Transport:** Chromium request streaming requires a working HTTP/2 or HTTP/3 path. This example uses HTTP/2 over TLS. A constructor feature probe does not certify proxy behavior; HTTP/1 uploads are not presented as supported browser streaming. CORS, authentication and authorization are application policies. The example exposes only its selected local origins, and intentionally has no authentication. Do not expose the sample to an untrusted network.

## 4. Reuse application beans

If the application already owns a compatible bounded AWS client, declare it as `S3AsyncClient`; the starter reuses it and creates no credentials provider. Its multipart/retry/buffering configuration must satisfy the base library's contract. If you expose a shared externally owned client as a bean, control destruction explicitly:

```java
@Bean(destroyMethod = "")
S3AsyncClient externallyOwnedClient() {
    return sharedClient;
}
```

Here `sharedClient` belongs to your application's lifecycle. A regular Spring-managed client bean may use `destroyMethod = "close"`. Supplying `ObjectStreams` skips all automatic AWS client/credentials creation, so a different storage backend can implement the existing interface. Supplying `StreamLimits` replaces the default limit bean. `ObjectStreamHandler` and the bean named `fileStreamsHandlerMapping` can also be replaced.

Do not register another raw WebHandler as the application's `webHandler` just to expose this endpoint. The starter adds a scoped HandlerMapping; normal controllers/router functions and application WebFilters remain in the dispatcher chain. The sample's `/example/status` is an independent application route.

## 5. Run the paired browser and backend example

For a real S3 backend, supply AWS credentials via the standard chain, create the bucket, and configure `S3_ENDPOINT`, `S3_BUCKET`, `S3_REGION` as needed. The sample defaults to a local Garage bucket/region, and the base repository contains `scripts/start-garage.sh` for an isolated development instance.

```bash
scripts/run-example.sh
```

This creates a temporary-development PEM certificate under ignored `build/tls`, starts `https://127.0.0.1:19443`, enables HTTP/2 and disables compression. Supply `DEMO_TLS_CERT` and `DEMO_TLS_KEY` to use your own development certificate. The local certificate is self-signed: trust it only in a disposable test context or replace it with a locally trusted certificate for manual browser use.

For a reproducible browser/HTTP test **without S3**, use the explicitly test-only bounded fixture:

```bash
scripts/run-example.sh :example:runTransportFixture
```

It uses the real starter, Boot server and WebFlux handler with [test-only storage](../../example/src/transportFixture/java/example/TransportFixture.java): at most 16 MiB per file, 32 MiB retained total and 16 objects. It aggregates these small fixture bodies in memory. It is excluded from production distributions and does not demonstrate S3 behavior. Restart it to clear test objects.

Run the companion React example on `https://localhost:5173` or `https://127.0.0.1:5173`, using the same `build/tls/cert.pem` and `key.pem` for local TLS. Set its endpoint to `https://localhost:19443/objects`. CORS exposes the length/range/ETag headers required for validation. The ordinary application probe is `GET /example/status`; `GET /objects/missing` returns the library's structured 404.

## 6. Verification and boundaries

`./gradlew test` covers import discovery, disabled-by-default behavior, typed limits, invalid configuration, user bean backoff, default-client destruction and HTTP opt-in. `./gradlew :example:installDist` builds the runnable application. [Verification record](../VERIFICATION.md) distinguishes actual checks from untested deployment behavior.

This release adds typed configuration and automatic wiring, not a new storage algorithm, file-processing workflow, security policy, retry guarantee or resumable-upload protocol. Keep authentication and per-object authorization in application filters before allowing access. Production proxies must preserve streaming and disable body buffering/compression for this endpoint. AWS/compatible-storage compatibility is inherited from the base adapter and still needs verification in your own environment.

[Spring Boot auto-configuration reference](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html) · [Reactive web reference](https://docs.spring.io/spring-boot/reference/web/reactive.html)

[← Versions](../index.md) · [README](../../README.md) · [RU](ru.md)
