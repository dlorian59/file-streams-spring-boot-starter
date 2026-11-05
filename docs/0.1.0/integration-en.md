# Account attachments: integration decisions

[← README](../../README.md) · [RU](integration-ru.md) · [Reference](reference-en.md) · [Development guide](en.md)

The [README](../../README.md) is the copyable existing-application path: one dependency, properties and an account-scoped controller. Keep your current Boot application entry point and authentication. No custom S3 bean, buffer conversion or download finalizer is required.

## Files to copy

- [AttachmentController.java](../../examples/consumer/src/main/java/com/example/attachments/AttachmentController.java): complete imports and EN/RU comments. Delegates the actual transfer to `ObjectStreamHandler.handle(exchange, key)`.
- [application.yml](../../examples/consumer/src/main/resources/application.yml): storage activation, bucket/region, named profile; the generic route stays disabled.
- [Gradle dependency consumer](../../examples/consumer/build.gradle.kts) or [Maven dependency consumer](../../examples/consumer/pom.xml): the same published-artifact boundary, without `project(...)` or composite-build dependencies.
- [ConsumerTest](../../examples/consumer/src/test/java/com/example/attachments/ConsumerTest.java): real Boot HTTP and Spring Security chain, default firewall, two identities, authorized transfer/range, 401/403, upload bounds and absence of an alternative generic route. Test storage is small in-memory storage, not S3. Basic credentials and disabled CSRF belong only to this test fixture.

## Account policy and transport

For principal `alice`, key `alice:invoice.pdf` is sent to `/attachments/alice%3Ainvoice.pdf`; the commit contains the original unencoded key. The controller accepts only stable account IDs matching `[A-Za-z0-9_-]+` and the exact `accountId:` prefix. Choose a different policy if your identity provider's principal is an email or opaque value outside that alphabet. Do not trust an account header, and do not enable `/objects/**` as an unprotected alternate route.

The colon avoids encoded path separators rejected by standard Spring Security firewalls. The lower-level handler still supports logical slash keys, but those require explicit application/proxy compatibility; do not globally relax the firewall to copy an example. `objectUrl` and `createFileStreams` encode the key once; encoding it twice changes the key or triggers firewall rejection.

The application's existing session or OAuth/resource-server setup supplies `Principal`. Keep its authorization and CSRF decisions. A cookie-authenticated browser may need an application CSRF header in `createFileStreams` operation headers. For cross-origin clients, configure a narrow CORS policy permitting PUT/GET, your authentication/CSRF headers, Content-Type, X-Upload-Length and Range. Expose ETag, Accept-Ranges, Content-Length, Content-Range and Content-Type. [Local CORS example](../../example/src/main/java/example/FileStreamsApplication.java) is demo-only and does not configure production security.

Spring WebFlux supports controller `Principal` and `ServerWebExchange` arguments directly: [official controller arguments](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/arguments.html). There is no servlet/MVC adapter here.

## Profiles

```yaml
file-streams:
  enabled: true
  limits:
    profile: attachments
    # Optional product-specific override; other defaults remain unchanged.
    max-object-size: 32MB
  s3:
    bucket: ${ATTACHMENTS_BUCKET}
    region: ${AWS_REGION}
```

Choose `attachments` (64 MiB), `media` (512 MiB) or `archives` (16 GiB). The omitted profile is `archives` to retain the original 16 GiB default. `max-object-size` explicitly overrides the profile; `max-buffer-size` defaults to 1 MiB and `max-concurrent-transfers` to two. Spring's MB/GB DataSize units are binary. Profiles change limits, not streaming algorithms. Java `StreamProfile` and TypeScript `fileProfiles` expose matching sizes, while the browser facade defaults to attachments. Set the same profile at both ends. Server upload and browser download checks are independent.

Bucket and region are application decisions. The default AWS credentials chain uses the application's workload role/environment/profile. An optional `s3.endpoint` selects compatible storage; do not put secrets in URLs or application.yml. Binding uses [standard Boot configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html); invalid enabled settings fail startup. The old explicit `Limits(DataSize,DataSize,int)` constructor remains available.

## Built-in route

If the application's filters already authenticate and authorize object keys, enable the ready route:

```yaml
file-streams:
  enabled: true
  limits:
    profile: attachments
  s3:
    bucket: ${ATTACHMENTS_BUCKET}
    region: ${AWS_REGION}
  http:
    enabled: true
```

This exposes PUT/GET `/objects/{encodedKey}` without a controller. Enabling the starter alone creates storage and a reusable `ObjectStreamHandler` in a reactive application, but exposes no generic route. A custom controller can inject the handler while `http.enabled=false`. Existing custom handler beans and mapping backoff rules remain in force. [Full bean ownership/reference](reference-en.md#beans-and-ownership).

## Browser and React

Configure `createFileStreams({objectsUrl: '/attachments', profile:'attachments'})`; `uploadFile(key,file)` automatically supplies length/type, and `download(key,destination)` handles response validation and destination lifetime. [Commented TypeScript application integration](https://github.com/dlorian59/file-streams/blob/main/browser/demo/application-files.ts). Use [FileAttachment](https://github.com/dlorian59/file-streams-react) for ready UI states. The application's authenticated transport remains responsible for credentials; a whole-file buffering/retry wrapper is not suitable.

Raw PUT carries a continuous body and optional X-Upload-Length. HTTP 201 is `{committed:true,key,bytes,etag}` only after storage commit. GET returns 200, a requested valid range 206; endpoints use the existing [HTTP contract](https://github.com/dlorian59/file-streams/blob/main/docs/HTTP-CONTRACT.md). Cancel is not rollback: unknown-length termination observed as EOF may commit a prefix; finalization or lost acknowledgements can leave an unknown result even with known length. Do not retry one-shot sources automatically.

## Verify your deployment

Use browser-to-origin HTTP/2 or HTTP/3 with TLS, preserving request streaming across the actual reverse proxy. HTTP/1 server compatibility does not imply browser streamed-upload support. Disable response compression on these byte/range routes. Grant the storage identity access only to the application's bucket/prefix. The starter neither creates buckets nor performs an availability/permissions check at startup.

For development run the [paired TLS example](en.md) and [consumer verification](../../examples/consumer/README.md). Their local-only certificates, public fixture credentials and test principal mechanism are not production configuration. [Verification evidence and limits](../VERIFICATION.md).

[← README](../../README.md) · [All properties and errors](reference-en.md)
