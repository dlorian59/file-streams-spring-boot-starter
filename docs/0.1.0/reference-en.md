# 0.1.0 — configuration and integration reference

[← Guide](en.md) · [Versions](../index.md) · [README](../../README.md) · [RU](reference-ru.md)

[Capability map](#capability-map) · [Configuration](#configuration) · [Beans and ownership](#beans-and-ownership) · [HTTP and browser](#http-and-browser) · [AOP decision](#aop-decision) · [Troubleshooting](#troubleshooting)

The starter reuses the existing file-streams S3 and WebFlux modules. It adds configuration and bean wiring, not a second byte-stream implementation. Prerequisites: JDK 17+, Spring Boot 4.1.2, locally built `file-streams:0.1.0` artifacts until registry publication; an S3-compatible bucket/credentials for real storage. HTTP integration requires a **reactive** application. Node 20+ and a supported browser are needed only for frontend examples. [Build/install steps](en.md#1-build-locally).

## Capability map

| Capability | Entry point / full source example | Preconditions and boundary |
| --- | --- | --- |
| Turn on storage wiring without a controller | `file-streams.enabled=true`; [application YAML](../../example/src/main/resources/application.yml) | Bucket/region required only when the corresponding default beans are created. |
| Expose raw streamed PUT/GET | `file-streams.http.enabled=true`; [Boot application](../../example/src/main/java/example/FileStreamsApplication.java) | Reactive web application, host security/CORS; default path `/objects/**`. |
| Bound uploads/admission | `FileStreamsProperties.Limits`; [ApplicationLimits](../../example/src/main/java/configuration/FileStreamsOverrides.java) | Upload size/chunk limits; shared S3 transfer slots; **not** a server download-size cap. |
| Use standard credentials discovery | DefaultCredentialsProvider created by starter | No keys embedded in properties. Environment/profile/workload role resolution follows AWS SDK. |
| Supply a named profile or own client | [ApplicationCredentials / ApplicationClient](../../example/src/main/java/configuration/FileStreamsOverrides.java) | Local `documents` profile must exist. Import both or supply another provider; own client skips default provider creation. |
| Supply another storage implementation | `ObjectStreams` bean; [bounded fixture example](../../example/src/transportFixture/java/example/TransportFixture.java) | Interface lifecycle still applies. Fixture aggregation is test-only and is not an S3 implementation. |
| Override handler or route precedence | [ApplicationHandler / ApplicationRouteOrder](../../example/src/main/java/configuration/FileStreamsOverrides.java) | Bean type/name backoff rules below; changing mapping does not change the handler's fixed path parser. |
| Run frontend/backend together | [TLS launcher](../../scripts/run-example.sh), [CORS and application route](../../example/src/main/java/example/FileStreamsApplication.java) | HTTP/2 TLS, permitted origins, visible response headers; no HTTP/1 streaming claim. |
| Diagnose activation/invalid config | [Auto-configuration](../../src/main/java/io/github/alex59/filestreams/boot/FileStreamsAutoConfiguration.java), [properties](../../src/main/java/io/github/alex59/filestreams/boot/FileStreamsProperties.java), [contract tests](../../src/test/java/io/github/alex59/filestreams/boot/FileStreamsAutoConfigurationTest.java) | Enabled invalid settings fail startup; overrides do not disable binding/validation of configured properties. |

The override classes are compiled with the example but sit outside its `example` component-scan package. They do not activate implicitly. Import only the required configuration in your own application, for example:

```java
@Import(FileStreamsOverrides.ApplicationLimits.class)
```

For a custom client/profile, import both `ApplicationClient.class` and `ApplicationCredentials.class`; keep `file-streams.s3.bucket` configured. Do not import every example indiscriminately. [Complete compiled source](../../example/src/main/java/configuration/FileStreamsOverrides.java) contains imports and paired EN/RU comments.

## Configuration

Public immutable records are `FileStreamsProperties(enabled, limits, s3, http)`, `Limits(maxObjectSize,maxBufferSize,maxConcurrentTransfers)`, `S3(bucket,region,endpoint)` and `Http(enabled)`, with their normal accessors. Defaults below are **Spring binding defaults** from `@DefaultValue`; callers constructing records directly must provide the constructor arguments. `@EnableConfigurationProperties` registration is supplied by the auto-configuration; applications need no manual registration.

All property names in this table are fully qualified. The build includes generated Spring configuration metadata for IDE completion.

| Property | Type / default | Validation and effect |
| --- | --- | --- |
| `file-streams.enabled` | boolean / false | Only true activates the starter's auto-configuration. Disabling it does not remove application-defined beans. |
| `file-streams.limits.max-object-size` | DataSize / 16GB | Nonnegative; 0 allows only empty uploads. Default S3 adapter also rejects effective StreamLimits.maxBytes >52,428,800,000 bytes (10,000 × 5 MiB). Used for upload, not existing-object download size. |
| `file-streams.limits.max-buffer-size` | DataSize / 1MB | 1–2,147,483,647 bytes, then converted to int. Bounds an upload source/network buffer. |
| `file-streams.limits.max-concurrent-transfers` | int / 2 | Positive; passed to default S3ObjectStreams. Upload/download share slots, with BUSY instead of an unbounded queue. A custom ObjectStreams controls its own admission. |
| `file-streams.s3.bucket` | String / unset | Nonblank when creating default ObjectStreams. No bucket creation, existence check or permission check at startup. |
| `file-streams.s3.region` | String / unset | Nonblank when creating the default S3 client. Custom clients choose their own region. |
| `file-streams.s3.endpoint` | URI / unset | Null means AWS endpoint; otherwise HTTP(S), host present, no URL credentials/query/fragment. Region/credential values are not embedded in the URI. |
| `file-streams.http.enabled` | boolean / false | True plus reactive application creates handler/mapping. It does not install authentication, CORS, request logging or virus scanning. |

Spring DataSize uses binary units: 1KB=1024 bytes, 1MB=1,048,576 bytes, 1GB=1,073,741,824 bytes. Host-side limits, network buffers and SDK memory remain additional concerns. A successful application startup does not prove that an S3 request will authenticate or the bucket exists.

### Defaults inherited from the base adapter

The starter deliberately has no separate knobs for AWS part size, retries or per-call timeout. It calls `S3Clients.bounded`: 5 MiB internal parts/threshold, 10 MiB SDK API buffer setting, one in-flight part; HTTP concurrency 8 and pending acquisition 16; connection timeout 5 s, read/write 60 s, attempt 60 s and API call 10 min; one attempt, path-style addressing, ambient proxy discovery disabled, checksum policy WHEN_REQUIRED. Total process memory is not bounded by the API buffer setting. For another verified profile provide your own compatible multipart-enabled `S3AsyncClient` bean. Do not enable replay of a one-shot source.

There is no property for endpoint prefix, CORS origins, access keys, automatic retry, content transformation or storage-object deletion. These are not silently inferred. The underlying `ObjectStreamHandler` expects `/objects/`; a custom mapping alone cannot make it parse `/files/`.

## Beans and ownership

[AutoConfiguration.imports](../../src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) registers `FileStreamsAutoConfiguration`. It is loaded through Boot; no package scan of the starter is required.

| Default bean name / type | Created when | Backoff and cleanup |
| --- | --- | --- |
| `fileStreamLimits` / StreamLimits | Integration enabled | Any StreamLimits bean wins. The record owns no resource. |
| `fileObjectStreams` / ObjectStreams | No application ObjectStreams bean | Uses supplied/default S3 client and limits. Supplying ObjectStreams skips the nested default S3/client/credentials configuration entirely. The store does not close an application client itself. |
| `fileStreamsS3Client` / S3AsyncClient | No ObjectStreams and no S3AsyncClient bean | Creates bounded client; Spring closes it. With a supplied client, the starter neither creates default credentials nor changes that client's destroy method. |
| `fileStreamsCredentialsProvider` / DefaultCredentialsProvider | Default client is being created and no AwsCredentialsProvider bean | Uses standard chain; Spring closes the created provider. Custom provider follows its own declared lifecycle. |
| `fileStreamsHttpHandler` / ObjectStreamHandler | Integration + HTTP enabled, reactive application | Any ObjectStreamHandler bean wins. Handler uses ObjectStreams + StreamLimits. |
| `fileStreamsHandlerMapping` / SimpleUrlHandlerMapping | Same HTTP conditions | Backoff by **exact bean name**, not every HandlerMapping bean. Default maps `/objects/**` at order -1; normal routes still work. |

With multiple candidates of a required type, use normal Spring qualification/primary selection; the starter does not pick an arbitrary bean. If a shared client is owned outside Spring, expose it with `@Bean(destroyMethod="")`; when Spring owns it, declare `destroyMethod="close"`. A supplied client must support the base profile's multipart/EOF/retry guarantees. Supplying a custom credentials provider alone still allows default client creation.

The bean lifecycle does not change transfer lifetimes: uploads retain admission until SDK termination/cleanup; downloads retain it until body completion, failure, cancellation or close. Never cache an open ObjectDownload. On shutdown, applications should coordinate termination of active work before disposing externally owned resources.

## HTTP and browser

The endpoint reuses the exact base HTTP contract; it does not introduce upload sessions or multipart/form-data. The [guide's paired TypeScript/Java flow](en.md#3-browser-to-java-contract) is the quick start. Full API inventories and annotated TS/Java files live in the sibling base checkout:

- [Base public API reference](https://github.com/59Alex/file-streams/blob/main/docs/0.1.0/reference-en.md).
- [TypeScript upload/download/range/errors source](https://github.com/59Alex/file-streams/blob/main/browser/demo/transfer-example.ts).
- [Java receive/deadline/download-lifetime source](https://github.com/59Alex/file-streams/blob/main/demo/src/main/java/io/github/alex59/filestreams/demo/TransferExamples.java).
- [React companion](https://github.com/59Alex/file-streams-react); its hooks/components wrap the same browser API.

Remote creation/publication is still pending; locally these files are under `../file-streams` and `../file-streams-react` next to this repository. The navigation links identify their intended repository locations rather than claiming remote availability.

| Request/result | Contract |
| --- | --- |
| PUT `/objects/{encodeURIComponent(key)}` | Raw bytes, optional Content-Type; prefer X-Upload-Length when size is known. No arbitrary header-to-S3 metadata propagation. |
| 201 | JSON `{committed:true,key,bytes,etag}` after validated EOF and storage acknowledgement. Browser checks all fields and source byte count. |
| GET same path | 200 streamed body; optional one Range gives 206 + Content-Range. Visible Content-Length, ETag, Accept-Ranges required by browser. Identity encoding only. |
| 400 | INVALID_REQUEST / LENGTH_MISMATCH / BUFFER_TOO_LARGE / CANCELLED / SOURCE_REUSED, committed=false. |
| 413 / 503 | TOO_LARGE / BUSY, committed=false. |
| 404 / 412 / 416 | NOT_FOUND / CHANGED / INVALID_RANGE, committed=false. 416 includes `bytes */total` only when total known. |
| 502 | STORAGE_FAILURE, committed=null: reconcile outcome. |
| Unsupported path/method | Empty 404 or 405 with Allow GET,PUT. CORS filter handles OPTIONS preflight. |

401/403 and application access policies belong to host filters. Later download failures terminate the already committed response instead of replacing it with JSON. Browser outcomes are not-started, rejected or unknown; progress is not a commit. The browser's `maxBytes` bounds downloads independently of the starter's upload-size settings.

Known length detects a short body. HTTP termination can be observed as normal EOF for unknown length, allowing a received prefix to be accepted. In the recorded fixture test, unknown-length abort retained 8,192 bytes; known-length abort produced a later 404. These observations are not a universal rollback guarantee or an S3 cleanup claim. [Evidence and limits](../VERIFICATION.md).

### Example-only runtime settings

[application.yml](../../example/src/main/resources/application.yml) chooses loopback:19443, HTTP/2 TLS and no compression. `S3_ENDPOINT` defaults to `http://127.0.0.1:19390`, `S3_BUCKET` to stream-tests, `S3_REGION` to garage; these environment aliases belong to the example YAML, not additional starter properties. `DEMO_TLS_CERT` / `DEMO_TLS_KEY` point to PEM files. The [launcher](../../scripts/run-example.sh) creates/renews ignored development certificates and forwards its optional first argument as a Gradle task; default is `:example:run`.

`runTransportFixture` is explicitly test-only: 16 MiB/file, 32 MiB stored total, 16 objects, two active uploads. It aggregates those bounded fixture bodies in memory, is excluded from production distributions and does not exercise S3. Its CORS origins are https://localhost:5173 and https://127.0.0.1:5173. The demo permits Content-Type/X-Upload-Length/Range and exposes Content-Type/Content-Length/ETag/Accept-Ranges/Content-Range/Content-Encoding. Auth headers/cookies require host changes, including credential-aware CORS; no permissive wildcard policy is added.

## AOP decision

No artificial aspect or extra AOP dependency is installed in this starter. Existing configuration eliminates manual storage/endpoint wiring. Repeating a proxied method returning a one-shot body can replay an invalid source; measuring method-return time measures creation of Mono, not transfer completion. A returned ObjectDownload still owns a live body, so even download-Mono success does not mean completed file delivery.

For observability use the reactive terminal signals or host HTTP filters and distinguish commit success, validation error, cancellation and unknown remote outcome. Do not subscribe inside advice, wrap resource values in a cache, or silently attach retry/repeat. Authorization and key access checks stay in host filters before body consumption. If applications add their own aspects, Spring self-invocation bypass and final-class/method proxy limits still matter; S3ObjectStreams and ObjectStreamHandler are final classes. [Spring proxy semantics](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html) · [Reactor lifecycle operators](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html).

## Troubleshooting

| Symptom | Likely cause / action |
| --- | --- |
| No ObjectStreams bean | Enable file-streams.enabled or provide a custom implementation; inspect Boot's condition report. |
| Storage works, `/objects` returns 404 | Enable HTTP and use a reactive application; use a nonempty encoded key. A servlet-only application does not activate this mapping. |
| `file-streams.s3.bucket/region is required` | Supply missing setting for the default bean being created; custom ObjectStreams/client changes which setting is needed. |
| DataSize/binding validation failure | Fix negative object size, buffer outside int range, nonpositive concurrency or malformed endpoint; overriding a bean does not suppress property validation. |
| Credentials fail only on first upload | Startup did not contact storage. Configure the normal SDK chain or your provider; verify region, endpoint, bucket and permissions. |
| Named profile example fails | Create the documents profile or replace its name; import provider with ApplicationClient. The example does not embed secrets. |
| CORS/probe succeeds but streaming fails | Verify TLS trust, negotiated HTTP/2/3, redirects, proxy buffering and allowed/exposed headers on the actual route. |
| Source reused, stale download, duplicate request | Remove retry/repeat/cache advice around one-shot resources; obtain a fresh source after reconciling outcome. |
| BUSY after cancel | SDK cleanup may still own the slot, or a download is abandoned. Consume/close it; do not remove bounds. |
| JDK 27 or Garage unavailable | Mark validation missing. The prior session found a missing JDK target and broken Docker snapshot; no repair or new S3 run is claimed. |

[← Guide](en.md) · [Versions](../index.md) · [README](../../README.md) · [RU](reference-ru.md)
