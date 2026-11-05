# file-streams-spring-boot-starter

<details name="readme-language">
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-en-dark.svg"><img src="assets/lang-en-light.svg" width="42" height="26" alt="EN"></picture></summary>

Upload attachments and download stored files in an existing Spring WebFlux application. The starter supplies streaming, S3 connections, size checks and resource cleanup. Your application decides who can access each file.

## Get started in your application

Java 17+, Spring Boot 4.1.2 **WebFlux**. The starter includes `spring-boot-starter-webflux`; this is not a Spring MVC adapter. These coordinates are not yet published in Central: use locally built artifacts as described in the [development guide](docs/0.1.0/en.md#1-build-locally).

Maven:

```xml
<dependency>
  <groupId>io.github.59alex</groupId>
  <artifactId>file-streams-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
dependencies {
    implementation("io.github.59alex:file-streams-spring-boot-starter:0.1.0")
}
```

`application.yml`:

```yaml
file-streams:
  enabled: true
  limits:
    profile: attachments
  s3:
    bucket: ${ATTACHMENTS_BUCKET}
    region: ${AWS_REGION}
```

The `attachments` profile allows 64 MiB per object, a 1 MiB source buffer and two simultaneous storage operations. AWS credentials use your existing workload role, environment or SDK profile. The generic `/objects/**` route stays disabled; the controller below authorizes its own `/attachments/{encodedKey}` route. Keep your application authentication: it must provide a trusted principal with a stable URL-safe account ID.

```text
src/main/resources/application.yml
src/main/java/com/example/attachments/AttachmentController.java
```

```java
package com.example.attachments;

import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public final class AttachmentController {
    private final ObjectStreamHandler files;

    public AttachmentController(ObjectStreamHandler files) {
        this.files = files;
    }

    @RequestMapping(path = "/attachments/{key}", method = {RequestMethod.PUT, RequestMethod.GET})
    public Mono<Void> transfer(@PathVariable("key") String key, Principal account, ServerWebExchange exchange) {
        if (account == null) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!account.getName().matches("[A-Za-z0-9_-]+") || !key.startsWith(account.getName() + ":")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
        }
        return files.handle(exchange, key);
    }
}
```

The browser sends key `alice:invoice.pdf` as one encoded segment, `alice%3Ainvoice.pdf`; only principal `alice` can read or replace it. Missing identity returns 401, another account returns 403 before the stream is consumed. Do not enable the generic route alongside this policy unless your application also protects that route.

### Send a file from the browser

```json
{ "dependencies": { "@59alex/file-streams": "0.1.0" } }
```

```ts
import { createFileStreams } from "@59alex/file-streams";

const files = createFileStreams({
  objectsUrl: "/attachments",
  profile: "attachments",
});
const commit = await files.uploadFile("alice:invoice.pdf", file);
await files.download(commit.key, destination);
```

`file` is a selected File; `destination` is an application-provided WritableStream. Your existing cookies/authenticated fetch supply credentials; do not treat a client account ID as authentication. npm publication is also pending. Browser uploads require working HTTP/2 or HTTP/3 over TLS through the complete proxy chain.

## Tasks and next steps

| Task | Result / complete example |
| --- | --- |
| Protect account attachments | [Controller + configuration + consumer test](examples/consumer): application policy before any body subscription |
| Pick attachment, media or archive bounds | [Profiles and overrides](docs/0.1.0/integration-en.md#profiles): one setting shared with the browser |
| Use the ready endpoint with existing filters | [Opt-in route](docs/0.1.0/integration-en.md#built-in-route): no custom controller |
| Connect React to real Spring/S3 | [Paired example](docs/0.1.0/en.md), [React component](https://github.com/dlorian59/file-streams-react) |

Cancellation or a lost response can leave the upload result unknown, including before intended EOF for unknown-length HTTP bodies. Do not automatically retry a one-shot stream. Supply a known size whenever available. Browser upload needs verified HTTP/2 or HTTP/3/TLS transport; no whole-file buffering fallback is provided.

[Integration / Подключение](docs/0.1.0/integration-en.md) · [Guide / Руководство](docs/0.1.0/en.md) · [Reference / Справочник](docs/0.1.0/reference-en.md) · [Versions / Версии](docs/index.md) · [Verification / Проверки](docs/VERIFICATION.md) · [MIT](LICENSE)

For video attachments, add [video-streams-spring-boot-starter](https://github.com/dlorian59/video-streams-spring-boot-starter). It reuses your configured ObjectStreams, prepares fMP4 HLS through FFmpeg and serves protected segments. The [paired React example](https://github.com/dlorian59/video-streams-react/blob/main/docs/en/integration.md) connects source upload, preparation and playback.

</details>

<details name="readme-language" open>
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-ru-dark.svg"><img src="assets/lang-ru-light.svg" width="42" height="26" alt="RU"></picture></summary>

Загрузка вложений и скачивание файлов в существующем приложении Spring WebFlux. Starter подключает потоковую передачу, S3, проверку размера и освобождение ресурсов. Приложение решает, кому доступен каждый файл.

## Подключение к вашему приложению

Java 17+, Spring Boot 4.1.2 **WebFlux**. `spring-boot-starter-webflux` уже включён; Spring MVC не поддерживается этим адаптером. Координаты пока не опубликованы в Central: используйте локально собранные артефакты по [руководству разработки](docs/0.1.0/ru.md#1-локальная-сборка).

Maven:

```xml
<dependency>
  <groupId>io.github.59alex</groupId>
  <artifactId>file-streams-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
dependencies {
    implementation("io.github.59alex:file-streams-spring-boot-starter:0.1.0")
}
```

`application.yml`:

```yaml
file-streams:
  enabled: true
  limits:
    profile: attachments
  s3:
    bucket: ${ATTACHMENTS_BUCKET}
    region: ${AWS_REGION}
```

Профиль `attachments`: до 64 МиБ на объект, буфер источника до 1 МиБ, две одновременные операции хранилища. AWS credentials берутся из роли приложения, окружения или SDK-профиля. Общий маршрут `/objects/**` остаётся выключенным; контроллер ниже проверяет права для `/attachments/{encodedKey}`. Сохраните аутентификацию приложения: она должна предоставлять доверенный principal со стабильным URL-safe account ID.

```text
src/main/resources/application.yml
src/main/java/com/example/attachments/AttachmentController.java
```

```java
package com.example.attachments;

import io.github.alex59.filestreams.webflux.ObjectStreamHandler;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public final class AttachmentController {
    private final ObjectStreamHandler files;

    public AttachmentController(ObjectStreamHandler files) {
        this.files = files;
    }

    @RequestMapping(path = "/attachments/{key}", method = {RequestMethod.PUT, RequestMethod.GET})
    public Mono<Void> transfer(@PathVariable("key") String key, Principal account, ServerWebExchange exchange) {
        if (account == null) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!account.getName().matches("[A-Za-z0-9_-]+") || !key.startsWith(account.getName() + ":")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
        }
        return files.handle(exchange, key);
    }
}
```

Браузер передаёт ключ `alice:invoice.pdf` одним сегментом `alice%3Ainvoice.pdf`; читать и заменять его может только principal `alice`. Без аутентификации ответ 401, другому аккаунту — 403 до чтения потока. Не включайте общий маршрут рядом с этой политикой без отдельной защиты в приложении.

### Отправить файл из браузера

```json
{ "dependencies": { "@59alex/file-streams": "0.1.0" } }
```

```ts
import { createFileStreams } from "@59alex/file-streams";

const files = createFileStreams({
  objectsUrl: "/attachments",
  profile: "attachments",
});
const commit = await files.uploadFile("alice:invoice.pdf", file);
await files.download(commit.key, destination);
```

`file` — выбранный File, `destination` — WritableStream приложения. Credentials передаёт существующая cookie-сессия или authenticated fetch; account ID клиента не является аутентификацией. Публикация npm также ещё не выполнена. Браузерной загрузке нужен работающий HTTP/2 или HTTP/3 с TLS по всей цепочке прокси.

## Задачи и дальнейшее чтение

| Задача | Результат / полный пример |
| --- | --- |
| Защитить вложения аккаунта | [Контроллер, конфигурация и consumer test](examples/consumer): проверка прав до подписки на тело |
| Выбрать лимиты вложений, медиа или архивов | [Профили и overrides](docs/0.1.0/integration-ru.md#профили): одна настройка вместе с браузером |
| Использовать готовый endpoint со своими фильтрами | [Маршрут по явному включению](docs/0.1.0/integration-ru.md#готовый-маршрут): свой контроллер не нужен |
| Соединить React с настоящим Spring/S3 | [Парный пример](docs/0.1.0/ru.md), [React-компонент](https://github.com/dlorian59/file-streams-react) |

Отмена или потерянный ответ могут оставить результат загрузки неизвестным, в том числе до ожидаемого конца HTTP-тела неизвестной длины. Одноразовый поток автоматически не повторяется. Передавайте известный размер всегда, когда он доступен. Для браузерной загрузки нужен проверенный HTTP/2 или HTTP/3/TLS; fallback с полным буфером файла не предусмотрен.

[Integration / Подключение](docs/0.1.0/integration-ru.md) · [Guide / Руководство](docs/0.1.0/ru.md) · [Reference / Справочник](docs/0.1.0/reference-ru.md) · [Versions / Версии](docs/index.md) · [Verification / Проверки](docs/VERIFICATION.md) · [MIT](LICENSE)

Для видеовложений подключите [video-streams-spring-boot-starter](https://github.com/dlorian59/video-streams-spring-boot-starter). Он использует настроенный ObjectStreams, готовит fMP4 HLS через FFmpeg и выдаёт защищённые сегменты. [Парный пример React](https://github.com/dlorian59/video-streams-react/blob/main/docs/ru/integration.md) связывает загрузку исходника, подготовку и воспроизведение.

</details>
