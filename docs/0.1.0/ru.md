# 0.1.0 — потоковые файлы со Spring Boot

[← Версии](../index.md) · [README](../../README.md) · [EN](en.md)

[Сборка](#1-локальная-сборка) · [S3](#2-поток-в-s3) · [HTTP](#3-контракт-браузера-и-java) · [Свои beans](#4-компоненты-приложения) · [Запуск](#5-совместный-запуск-браузера-и-backend) · [Проверки](#6-проверки-и-границы)

[Полный API, параметры, ошибки и диагностика](reference-ru.md)

## 1. Локальная сборка

Разместите `file-streams` и этот репозиторий рядом. Нужен JDK 17+; bytecode остаётся Java 17. На 21/25/27 используется тот же API без preview-функций. Для браузерных примеров нужен Node 20+, для локального TLS — OpenSSL.

Из каталога starter:

```bash
../file-streams/gradlew -p ../file-streams build publishToMavenLocal \
  -Dmaven.repo.local="$PWD/../file-streams/build/local-repository"
./gradlew build :example:installDist
./gradlew publishToMavenLocal
```

Проект-потребитель получает starter из Maven Local, а базовые модули из отдельного каталога. Укажите путь к своему checkout:

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

В зависимости входят Boot WebFlux и S3/WebFlux-модули. Эти команды не публикуют пакеты в реестры. [Конфигурация сборки](../../build.gradle.kts).

## 2. Поток в S3

**Задача:** загружать документы без накопления целого файла в памяти приложения и отдельного API multipart-сессий. Движок `file-streams` уже проверяет длину, соблюдает backpressure, ограничивает буферы AWS и отличает прочитанные байты от сохранённого объекта.

Достаточно зависимости, обычного `@SpringBootApplication` и конфигурации:

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

Контроллер и bean S3-клиента писать не нужно. [Приложение](../../example/src/main/java/example/FileStreamsApplication.java) · [полная конфигурация](../../example/src/main/resources/application.yml) · [автоконфигурация](../../src/main/java/io/github/alex59/filestreams/boot/FileStreamsAutoConfiguration.java).

| Настройка | По умолчанию | Значение |
| --- | --- | --- |
| `file-streams.enabled` | `false` | Явное включение интеграции. |
| `s3.bucket` | не задан | Нужен для создания `ObjectStreams`. |
| `s3.region` | не задан | Нужен для создания AWS-клиента. |
| `s3.endpoint` | не задан | Без него AWS; HTTP(S) URL для совместимого хранилища. |
| `limits.max-object-size` | `16GB` | Максимальная объявленная и фактическая длина upload; не предел размера download. |
| `limits.max-buffer-size` | `1MB` | Максимальный отдельный буфер источника/сети при upload. |
| `limits.max-concurrent-transfers` | `2` | Общие слоты upload/download; лишний запрос получает `BUSY`. |
| `http.enabled` | `false` | Подключение `/objects/**` в reactive-приложении. |

Размеры используют Spring `DataSize`: `1MB` — 1 048 576 байт. Неверные лимиты отклоняются при запуске. Профиль S3 также ограничен 10 000 внутренними частями по 5 MiB. Credentials берутся из стандартной цепочки AWS SDK; полей для секретов в starter нет. Созданные ресурсы закрываются вместе с контекстом. Скачивание удерживает слот до завершения, ошибки, отмены или закрытия тела.

## 3. Контракт браузера и Java

**Задача:** отправить File, показывать чтение источника и отдельно подтвердить сохранение. TypeScript использует тот же `@59alex/file-streams`:

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

`file`, `key` и `destination` предоставляет приложение. Writable destination может дать поддерживаемый браузером файловый picker. Целый Blob для передачи не нужен. [React-компоненты](https://github.com/59Alex/file-streams-react) управляют UI поверх этого API; companion-репозиторий пока подготовлен локально.

На Java готовый адаптер вызывает `ObjectStreams.upload(request, requestBody)` и `ObjectStreams.download(key, rangeHeader)`. Starter использует эти алгоритмы и их сериализацию ответа.

| Запрос | Ответ / поведение |
| --- | --- |
| `PUT /objects/{encodeURIComponent(key)}`, сырые байты | `201 application/json`: `{ "committed": true, "key": "report.bin", "bytes": 123, "etag": "opaque" }`. |
| Необязательный `X-Upload-Length` | Сверяется с фактической длиной; конфликт с Content-Length отклоняется. |
| `GET` по тому же адресу | `200`, Content-Length, ETag, Accept-Ranges: bytes и один поток тела. |
| `GET` с одиночным `Range` | `206` с проверенным Content-Range; неверный диапазон — `416`. |
| Известный отказ проверки | Структурированная ошибка с `committed: false`. |
| Неопределённость хранилища/финализации | `committed: null`; проверьте объект перед решением о повторе. |

Прогресс чтения не означает commit. Отмена не откатывает объект, который успел сохраниться во время финализации. Потоки одноразовые, автоматического replay нет. ETag — непрозрачный идентификатор, а не checksum. При отмене скачивания часть данных уже может быть записана в destination.

Локальная отмена подписки или ошибка источника отличаются от завершения HTTP-запроса. Если транспорт передаёт завершение как обычный EOF при неизвестной длине, backend может принять полученный префикс. Поэтому browser abort остаётся неизвестным исходом даже до предполагаемого конца источника. Передавайте `knownLength` / `X-Upload-Length`, когда итоговый размер известен: это обнаруживает короткое тело, но не превращает отмену в гарантию отката.

**Транспорт:** для потокового запроса Chromium нужен реально работающий HTTP/2 или HTTP/3. Пример использует HTTP/2 с TLS. Успешная проверка конструктора Request не доказывает работу прокси; HTTP/1 не объявляется поддерживаемым браузерным streaming upload. CORS, аутентификацию и права задаёт приложение. Пример разрешает только выбранные локальные origins и не содержит аутентификации — ограничьте его доступность локальной средой.

## 4. Компоненты приложения

Готовый совместимый bounded AWS-клиент можно предоставить bean типа `S3AsyncClient`. Starter использует его и не создаёт credentials provider. Его multipart/retry/buffer-настройки должны соответствовать контракту базового адаптера. Для внешнего общего клиента явно сохраните владение:

```java
@Bean(destroyMethod = "")
S3AsyncClient externallyOwnedClient() {
    return sharedClient;
}
```

`sharedClient` здесь принадлежит жизненному циклу приложения. Обычный управляемый Spring-клиент может использовать `destroyMethod = "close"`. Bean `ObjectStreams` полностью заменяет хранилище и исключает создание AWS-клиента/credentials. Bean `StreamLimits` заменяет лимиты. Также можно заменить `ObjectStreamHandler` или bean с именем `fileStreamsHandlerMapping`.

Не подменяйте главный `webHandler` приложения ради одного endpoint. Starter добавляет scoped HandlerMapping, сохраняя общий dispatcher, WebFilters и обычные маршруты. В примере `/example/status` — независимый RouterFunction приложения.

## 5. Совместный запуск браузера и backend

Для настоящего S3 задайте credentials стандартным способом AWS, создайте bucket и при необходимости настройте `S3_ENDPOINT`, `S3_BUCKET`, `S3_REGION`. Значения примера рассчитаны на локальный Garage; отдельный fixture запускается скриптом `scripts/start-garage.sh` базового репозитория.

```bash
scripts/run-example.sh
```

Скрипт создаёт сертификат разработки в игнорируемом `build/tls`, запускает `https://127.0.0.1:19443`, включает HTTP/2 и отключает compression. Свои сертификат и ключ передайте через `DEMO_TLS_CERT` и `DEMO_TLS_KEY`. Самоподписанному сертификату доверяйте только в отдельном тестовом контексте; для ручной проверки замените его локально доверенным.

Для проверки браузерного/HTTP-контракта **без S3** есть явно тестовый fixture:

```bash
scripts/run-example.sh :example:runTransportFixture
```

Он использует настоящий starter, Boot и WebFlux с [тестовым хранилищем](../../example/src/transportFixture/java/example/TransportFixture.java): максимум 16 MiB на файл, 32 MiB сохранённых данных и 16 объектов. Небольшие тестовые тела здесь собираются в памяти. Fixture не входит в production distribution и не проверяет работу S3. Перезапуск удаляет данные.

React-пример запускается на `https://localhost:5173` или `https://127.0.0.1:5173`; для TLS можно использовать те же `build/tls/cert.pem` и `key.pem`. Endpoint: `https://localhost:19443/objects`. CORS открывает заголовки длины, диапазона и ETag для проверки. `GET /example/status` проверяет маршрут приложения; `GET /objects/missing` возвращает структурированный 404 библиотеки.

## 6. Проверки и границы

`./gradlew test` проверяет обнаружение imports, выключение по умолчанию, лимиты и ошибки конфигурации, приоритет пользовательских beans, закрытие созданного клиента и HTTP opt-in. `./gradlew :example:installDist` собирает запускаемое приложение. [Запись проверок](../VERIFICATION.md) отделяет реально выполненное от непроверенных окружений.

Версия добавляет типизированную конфигурацию и автоматическое подключение, сохраняя существующий механизм хранения. Здесь нет отдельного workflow обработки файлов, политики доступа, гарантии повторов или протокола возобновляемой загрузки. Добавьте аутентификацию и права на конкретные объекты в фильтрах приложения. Production-прокси должен сохранять streaming и не буферизовать/сжимать тела этого endpoint. Совместимость AWS и других хранилищ требует проверки в вашей среде.

[Справочник автоконфигурации Spring Boot](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html) · [Reactive web](https://docs.spring.io/spring-boot/reference/web/reactive.html)

[← Версии](../index.md) · [README](../../README.md) · [EN](en.md)
