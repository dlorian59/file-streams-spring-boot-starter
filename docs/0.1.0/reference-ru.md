# 0.1.0 — справочник конфигурации и интеграции

[← Руководство](ru.md) · [Версии](../index.md) · [README](../../README.md) · [EN](reference-en.md)

[Карта возможностей](#карта-возможностей) · [Конфигурация](#конфигурация) · [Beans и ресурсы](#beans-и-ресурсы) · [HTTP и браузер](#http-и-браузер) · [Решение об AOP](#решение-об-aop) · [Диагностика](#диагностика)

Starter использует существующие S3/WebFlux-модули file-streams и добавляет конфигурацию/beans, сохраняя единый движок потоковой передачи. Нужны JDK 17+, Spring Boot 4.1.2, локально собранные артефакты file-streams:0.1.0 до публикации в реестре; для настоящего хранилища — S3-compatible bucket и credentials. HTTP-интеграция требует **reactive**-приложение. Node 20+ и поддерживаемый браузер нужны только frontend-примерам. [Сборка и установка](ru.md#1-локальная-сборка).

## Карта возможностей

| Возможность | Настройка / полный пример | Условия и граница |
| --- | --- | --- |
| Подключить хранилище без контроллера | `file-streams.enabled=true`; [application YAML](../../example/src/main/resources/application.yml) | Bucket/region нужны только при создании соответствующих default beans. |
| Подключить потоковые PUT/GET | `file-streams.http.enabled=true`; [Boot-приложение](../../example/src/main/java/example/FileStreamsApplication.java) | Reactive-приложение, host security/CORS; стандартный путь `/objects/**`. |
| Ограничить upload/admission | `FileStreamsProperties.Limits`; [ApplicationLimits](../../example/src/main/java/configuration/FileStreamsOverrides.java) | Лимиты upload/его порций и общие S3-слоты; **не** предел размера скачиваемого объекта. |
| Использовать стандартную цепочку credentials | Starter создаёт DefaultCredentialsProvider | Ключи не встраиваются в properties. Окружение/профиль/роль среды обрабатываются AWS SDK. |
| Подключить профиль или свой клиент | [ApplicationCredentials / ApplicationClient](../../example/src/main/java/configuration/FileStreamsOverrides.java) | Профиль documents должен существовать. Импортируйте обе конфигурации либо свой provider; custom client отключает создание default provider. |
| Заменить хранилище | Bean ObjectStreams; [bounded fixture](../../example/src/transportFixture/java/example/TransportFixture.java) | Контракт ресурсов интерфейса сохраняется. Накопление в fixture только для теста; это не S3-реализация. |
| Заменить handler/приоритет mapping | [ApplicationHandler / ApplicationRouteOrder](../../example/src/main/java/configuration/FileStreamsOverrides.java) | Правила backoff по типу/имени ниже; замена mapping не меняет фиксированный parser пути handler. |
| Запустить frontend/backend вместе | [TLS-скрипт](../../scripts/run-example.sh), [CORS и маршрут приложения](../../example/src/main/java/example/FileStreamsApplication.java) | HTTP/2 TLS, разрешённые origins, видимые заголовки; streaming HTTP/1 не заявляется. |
| Найти причину неактивной/неверной конфигурации | [Автоконфигурация](../../src/main/java/io/github/alex59/filestreams/boot/FileStreamsAutoConfiguration.java), [properties](../../src/main/java/io/github/alex59/filestreams/boot/FileStreamsProperties.java), [контрактные тесты](../../src/test/java/io/github/alex59/filestreams/boot/FileStreamsAutoConfigurationTest.java) | Ошибки включённой конфигурации останавливают startup; bean override не отключает binding/validation заданных properties. |

Override-классы компилируются вместе с примером, но находятся вне его component scan пакета example. Сами они не включаются. Импортируйте выбранную конфигурацию в своём приложении:

```java
@Import(FileStreamsOverrides.ApplicationLimits.class)
```

Для своего клиента/профиля импортируйте ApplicationClient.class вместе с ApplicationCredentials.class и сохраните file-streams.s3.bucket. Не подключайте все примеры одновременно без необходимости. [Полный компилируемый исходник](../../example/src/main/java/configuration/FileStreamsOverrides.java) содержит imports и соседние комментарии EN/RU.

## Конфигурация

Публичные immutable records: `FileStreamsProperties(enabled, limits, s3, http)`, `Limits(maxObjectSize,maxBufferSize,maxConcurrentTransfers)`, `S3(bucket,region,endpoint)`, `Http(enabled)` и обычные accessors полей. Значения ниже — **defaults Spring binding** через @DefaultValue. При прямом new record нужно передать аргументы конструктора самостоятельно. Автоконфигурация сама регистрирует @EnableConfigurationProperties; вручную регистрировать properties не нужно.

Таблица использует полные имена. В артефакт входит сгенерированная Spring configuration metadata для автодополнения IDE.

| Property | Тип / default | Проверка и действие |
| --- | --- | --- |
| `file-streams.enabled` | boolean / false | Только true включает автоконфигурацию starter. Выключение не удаляет beans, определённые приложением. |
| `file-streams.limits.max-object-size` | DataSize / 16GB | Неотрицателен; 0 разрешает только пустые uploads. Default S3-адаптер также отклоняет эффективный StreamLimits.maxBytes >52 428 800 000 байт (10 000 × 5 MiB). Не ограничивает размер download существующего объекта. |
| `file-streams.limits.max-buffer-size` | DataSize / 1MB | 1–2 147 483 647 байт, затем преобразуется в int. Ограничивает отдельный upload/source/network buffer. |
| `file-streams.limits.max-concurrent-transfers` | int / 2 | Положителен; передаётся default S3ObjectStreams. Upload/download делят слоты, лишние запросы получают BUSY. Свой ObjectStreams управляет собственным admission. |
| `file-streams.s3.bucket` | String / не задан | Непустой при создании default ObjectStreams. Bucket не создаётся, наличие/права при startup не проверяются. |
| `file-streams.s3.region` | String / не задан | Непустой при создании default S3 client. Собственный клиент выбирает свой region. |
| `file-streams.s3.endpoint` | URI / не задан | Null означает AWS; иначе HTTP(S) с host, без URL credentials/query/fragment. Region/credentials не встраиваются в URI. |
| `file-streams.http.enabled` | boolean / false | True и reactive-приложение создают handler/mapping. Аутентификация, CORS, request logging и virus scanning не устанавливаются. |

Spring DataSize использует двоичные единицы: 1KB=1024, 1MB=1 048 576, 1GB=1 073 741 824 байта. Лимиты host, сетевые буферы и память SDK учитываются отдельно. Успешный startup не доказывает наличие bucket или успешную будущую аутентификацию в S3.

### Настройки базового адаптера

Starter намеренно не вводит отдельные knobs для AWS part size, retries и per-call timeout. Он вызывает S3Clients.bounded: части/порог 5 MiB, API-буфер SDK 10 MiB, одна часть в полёте; HTTP concurrency 8, pending acquisition 16; connection timeout 5 с, read/write 60 с, attempt 60 с, API call 10 мин; одна попытка, path-style addressing, отключённое наследование proxy, checksums WHEN_REQUIRED. Это не ограничение общей памяти процесса. Другой проверенный профиль передаётся собственным multipart-enabled S3AsyncClient bean. Не включайте replay одноразового источника.

Нет properties для prefix endpoint, CORS origins, access keys, автоматических повторов, преобразования содержимого или удаления объектов. Они не выводятся неявно из других настроек. ObjectStreamHandler ожидает `/objects/`: одна замена mapping не научит его разбирать `/files/`.

## Beans и ресурсы

[AutoConfiguration.imports](../../src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) регистрирует FileStreamsAutoConfiguration. Она загружается Boot; сканировать пакет starter вручную не требуется.

| Имя default bean / тип | Условие создания | Backoff и очистка |
| --- | --- | --- |
| `fileStreamLimits` / StreamLimits | Интеграция включена | Любой StreamLimits bean имеет приоритет. Record не владеет ресурсом. |
| `fileObjectStreams` / ObjectStreams | Нет ObjectStreams приложения | Использует свой/default клиент и лимиты. Собственный ObjectStreams полностью отключает вложенную default S3/client/credentials configuration. Сам store не закрывает клиент приложения. |
| `fileStreamsS3Client` / S3AsyncClient | Нет ObjectStreams и S3AsyncClient | Создаётся bounded client; Spring закрывает его. Для своего клиента starter не создаёт default credentials и не меняет destroy method. |
| `fileStreamsCredentialsProvider` / DefaultCredentialsProvider | Создаётся default client, нет AwsCredentialsProvider | Стандартная цепочка; созданный provider закрывает Spring. Свой provider сохраняет объявленный жизненный цикл. |
| `fileStreamsHttpHandler` / ObjectStreamHandler | Integration + HTTP включены, reactive-приложение | Любой ObjectStreamHandler bean имеет приоритет. Handler использует ObjectStreams и StreamLimits. |
| `fileStreamsHandlerMapping` / SimpleUrlHandlerMapping | Те же HTTP-условия | Backoff по **точному имени bean**, а не любому HandlerMapping. По умолчанию `/objects/**` с order=-1; обычные маршруты продолжают работать. |

При нескольких candidates используйте обычные Spring qualifier/primary rules; starter не выбирает произвольный bean. Внешний общий клиент можно предоставить с @Bean(destroyMethod=""), сохранив внешнее владение; если владельцем является Spring, используйте destroyMethod="close". Клиент должен соблюдать multipart/EOF/retry-контракт базового адаптера. Если заменить только credentials provider, default client продолжит создаваться.

Жизненный цикл beans не меняет lifetime передачи: upload удерживает admission до завершения/очистки SDK; download — до завершения тела, ошибки, отмены или close. Открытый ObjectDownload нельзя кэшировать как общее значение. При остановке приложение согласует завершение активной работы до закрытия внешних ресурсов.

## HTTP и браузер

Endpoint использует исходный HTTP-контракт, без дополнительных upload sessions и multipart/form-data. [Парный TS/Java-сценарий](ru.md#3-контракт-браузера-и-java) — быстрый старт. Полный API и комментированные исходники находятся в соседнем checkout:

- [Справочник базового API](https://github.com/dlorian59/file-streams/blob/main/docs/0.1.0/reference-ru.md).
- [TypeScript upload/download/range/errors](https://github.com/dlorian59/file-streams/blob/main/browser/demo/transfer-example.ts).
- [Java receive/deadline/download lifetime](https://github.com/dlorian59/file-streams/blob/main/demo/src/main/java/io/github/alex59/filestreams/demo/TransferExamples.java).
- [React companion](https://github.com/dlorian59/file-streams-react): hooks/components поверх того же browser API.

Клонируйте `file-streams` и `file-streams-react` рядом с этим репозиторием, чтобы выполнить парные примеры локально. Ссылки выше открывают соответствующие исходники на GitHub. Публикация пакетов в реестрах выполняется отдельно.

| Запрос/результат | Контракт |
| --- | --- |
| PUT `/objects/{encodeURIComponent(key)}` | Сырые байты, необязательный Content-Type; X-Upload-Length предпочтителен при известном размере. Произвольные headers не переносятся в S3 metadata. |
| 201 | JSON `{committed:true,key,bytes,etag}` после проверенного EOF и storage acknowledgement. Browser сверяет поля и число прочитанных байтов. |
| GET того же адреса | 200 и поток; одиночный Range даёт 206 + Content-Range. Browser требует видимые Content-Length, ETag, Accept-Ranges и identity encoding. |
| 400 | INVALID_REQUEST / LENGTH_MISMATCH / BUFFER_TOO_LARGE / CANCELLED / SOURCE_REUSED, committed=false. |
| 413 / 503 | TOO_LARGE / BUSY, committed=false. |
| 404 / 412 / 416 | NOT_FOUND / CHANGED / INVALID_RANGE, committed=false. В 416 bytes */total только при известном размере. |
| 502 | STORAGE_FAILURE, committed=null: нужно сверить исход. |
| Неподдерживаемый путь/метод | Пустой 404 либо 405 с Allow GET,PUT. OPTIONS preflight обслуживает CORS filter. |

401/403 и правила доступа принадлежат host filters. Поздняя ошибка download разрывает уже отправляемый ответ вместо замены его на JSON. Browser outcomes: not-started, rejected, unknown; прогресс не означает commit. Browser maxBytes отдельно ограничивает download, независимо от upload-настроек starter.

Известная длина позволяет отклонить короткое тело. HTTP termination может быть представлен как нормальный EOF неизвестного потока, и полученный префикс способен сохраниться. В записанном fixture-сценарии unknown-length abort оставил 8 192 байта, а known-length abort дал последующий 404. Это не универсальная гарантия отката и не доказательство очистки S3. [Evidence и границы](../VERIFICATION.md).

### Настройки только примера

[application.yml](../../example/src/main/resources/application.yml) задаёт loopback:19443, HTTP/2 TLS и отключённое сжатие. S3_ENDPOINT по умолчанию `http://127.0.0.1:19390`, S3_BUCKET — stream-tests, S3_REGION — garage. Эти environment aliases принадлежат YAML примера, не являются дополнительными properties starter. DEMO_TLS_CERT/DEMO_TLS_KEY указывают на PEM. [Launcher](../../scripts/run-example.sh) создаёт/обновляет игнорируемые сертификаты разработки и передаёт необязательный первый аргумент как Gradle task; default — `:example:run`.

runTransportFixture предназначен только для теста: 16 MiB/файл, 32 MiB сохранённых данных, 16 объектов, две активные загрузки. Эти небольшие тела собираются в памяти. Fixture не входит в production distribution и не проверяет S3. Его CORS origins — https://localhost:5173 и https://127.0.0.1:5173. Demo разрешает Content-Type/X-Upload-Length/Range и открывает Content-Type/Content-Length/ETag/Accept-Ranges/Content-Range/Content-Encoding. Auth headers/cookies требуют изменений host policy, включая credential-aware CORS; разрешающий всё wildcard не добавляется.

## Решение об AOP

В этом starter нет искусственного aspect и дополнительной AOP-зависимости. Существующая конфигурация уже убирает ручное подключение storage/endpoint. Повтор проксированного метода с одноразовым body способен повторно использовать недействительный источник; замер возврата метода измеряет создание Mono, а не завершение передачи. ObjectDownload возвращает живое тело, поэтому даже успех download-Mono не означает доставку всего файла.

Для наблюдения используйте reactive terminal signals или HTTP-фильтры host, различая commit success, validation error, cancellation и неизвестный удалённый исход. Не подписывайтесь внутри advice, не кэшируйте resource values и не добавляйте retry/repeat скрыто. Авторизация и права на ключ остаются в host filters до чтения тела. Если приложение добавляет свой aspect, действуют обход proxy при self-invocation и ограничения final-классов/методов; S3ObjectStreams и ObjectStreamHandler являются final. [Spring proxy](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html) · [Reactor lifecycle](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html).

## Диагностика

| Симптом | Причина / действие |
| --- | --- |
| Нет ObjectStreams bean | Включите file-streams.enabled либо предоставьте реализацию; смотрите Boot condition report. |
| Хранилище работает, `/objects` возвращает 404 | Включите HTTP, используйте reactive-приложение и непустой encoded key. Servlet-only приложение не включает mapping. |
| `file-streams.s3.bucket/region is required` | Задайте setting для создаваемого default bean; свой ObjectStreams/client меняет набор нужных settings. |
| DataSize/binding validation failure | Исправьте отрицательный размер, buffer вне int, неположительную concurrency либо endpoint. Bean override не отключает validation properties. |
| Credentials падают только при первом upload | Startup не обращался к storage. Настройте стандартную цепочку/provider, проверьте region, endpoint, bucket и права. |
| Не работает named profile пример | Создайте documents profile либо смените имя; вместе с ApplicationClient импортируйте provider. Secrets в примере нет. |
| CORS/probe проходит, streaming — нет | Проверьте доверие TLS, HTTP/2/3, redirects, буферизацию и разрешённые/видимые headers реального маршрута. |
| Повтор источника, stale download, двойной запрос | Уберите retry/repeat/cache advice вокруг one-shot resources; создайте новый source после сверки исхода. |
| BUSY после cancel | SDK cleanup ещё владеет слотом или download оставлен открытым. Прочитайте/закройте его, не снимайте bounds. |
| Нет JDK 27/Garage | Отметьте непроведённую проверку. Ранее обнаружены отсутствующая цель JDK и повреждённый Docker snapshot; ремонт и новый S3-запуск не заявлены. |

[← Руководство](ru.md) · [Версии](../index.md) · [README](../../README.md) · [EN](reference-en.md)
