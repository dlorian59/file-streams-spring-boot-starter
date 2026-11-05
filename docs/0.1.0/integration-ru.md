# Вложения аккаунта: решения при подключении

[← README](../../README.md) · [EN](integration-en.md) · [Справочник](reference-ru.md) · [Разработка](ru.md)

[README](../../README.md) содержит копируемое подключение к существующему приложению: зависимость, свойства и контроллер аккаунта. Сохраните entry point Boot и свою аутентификацию. Писать S3 bean, преобразование буферов или финализатор скачивания не нужно.

## Файлы для приложения

- [AttachmentController.java](../../examples/consumer/src/main/java/com/example/attachments/AttachmentController.java): imports и комментарии EN/RU; сам поток передаётся в `ObjectStreamHandler.handle(exchange,key)`.
- [application.yml](../../examples/consumer/src/main/resources/application.yml): активация, bucket/region и профиль; общий маршрут выключен.
- [Gradle consumer](../../examples/consumer/build.gradle.kts) или [Maven consumer](../../examples/consumer/pom.xml): зависимость на артефакт, без `project(...)` и composite build.
- [ConsumerTest](../../examples/consumer/src/test/java/com/example/attachments/ConsumerTest.java): настоящий Boot HTTP и Spring Security chain, стандартный firewall, два пользователя, transfer/range, 401/403, лимит загрузки и отсутствие обходного общего маршрута. Малое тестовое хранилище в памяти — не S3. Basic credentials и выключенный CSRF используются только в этом тесте.

## Политика аккаунта и транспорт

Для principal `alice` ключ `alice:invoice.pdf` передаётся на `/attachments/alice%3Ainvoice.pdf`; commit содержит исходный ключ без кодирования. Контроллер допускает стабильные account ID по `[A-Za-z0-9_-]+` и точный префикс `accountId:`. Если principal identity provider содержит email или другой формат, выберите соответствующую политику. Не доверяйте account-header и не включайте `/objects/**` как альтернативный незащищённый маршрут.

Двоеточие позволяет избежать закодированных разделителей пути, которые отклоняет стандартный firewall Spring Security. Низкоуровневый handler сохраняет slash-ключи, но их совместимость с приложением/прокси проверяется отдельно. Не ослабляйте firewall глобально ради примера. `objectUrl` и `createFileStreams` кодируют ключ один раз; двойное кодирование меняет ключ или приводит к отказу firewall.

`Principal` предоставляет существующая сессия или OAuth/resource server приложения. Сохраните его правила authorization и CSRF. При cookie-аутентификации браузеру может понадобиться CSRF-header в параметрах операции `createFileStreams`. Для cross-origin настройте узкий CORS: PUT/GET, свои auth/CSRF headers, Content-Type, X-Upload-Length, Range; откройте ETag, Accept-Ranges, Content-Length, Content-Range, Content-Type. [Локальный CORS-пример](../../example/src/main/java/example/FileStreamsApplication.java) — демо, не production security.

Аргументы контроллера `Principal` и `ServerWebExchange` поддерживаются WebFlux напрямую: [официальная документация](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/arguments.html). Servlet/MVC adapter здесь отсутствует.

## Профили

```yaml
file-streams:
  enabled: true
  limits:
    profile: attachments
    # Необязательный лимит продукта; прочие defaults сохраняются.
    max-object-size: 32MB
  s3:
    bucket: ${ATTACHMENTS_BUCKET}
    region: ${AWS_REGION}
```

`attachments` — 64 МиБ, `media` — 512 МиБ, `archives` — 16 ГиБ. Без профиля сохраняется прежний `archives`/16 ГиБ. Явный `max-object-size` заменяет лимит профиля; `max-buffer-size` по умолчанию 1 МиБ, `max-concurrent-transfers` — два. MB/GB в Spring DataSize бинарные. Профили меняют ограничения, не алгоритм передачи. Java `StreamProfile` и TypeScript `fileProfiles` содержат одинаковые размеры, но браузерный facade по умолчанию использует attachments. Согласуйте профиль обеих сторон. Серверная загрузка и браузерное скачивание ограничиваются независимо.

Bucket/region выбирает приложение. Стандартная цепочка AWS использует workload role, окружение или профиль приложения. `s3.endpoint` выбирает совместимое хранилище; секреты не нужны в URL/application.yml. Binding выполняется [стандартным Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html); неверные активные свойства останавливают запуск. Старый конструктор `Limits(DataSize,DataSize,int)` сохранён.

## Готовый маршрут

Если фильтры приложения уже аутентифицируют и проверяют права на ключи, включите готовый маршрут:

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

PUT/GET `/objects/{encodedKey}` появляются без контроллера. Одно включение starter создаёт хранилище и переиспользуемый `ObjectStreamHandler` в reactive-приложении, но не открывает общий маршрут. Свой контроллер может внедрять handler при `http.enabled=false`. Прежние правила backoff для пользовательского handler/mapping сохраняются. [Beans и владение](reference-ru.md).

## Браузер и React

Настройте `createFileStreams({objectsUrl:'/attachments',profile:'attachments'})`. `uploadFile(key,file)` передаёт размер/тип; `download(key,destination)` проверяет ответ и управляет назначением. [TypeScript-пример с комментариями](https://github.com/dlorian59/file-streams/blob/main/browser/demo/application-files.ts). Для готовых UI-состояний используйте [FileAttachment](https://github.com/dlorian59/file-streams-react). Credentials остаются ответственностью транспорта приложения; wrapper с полной буферизацией или автоповтором тела не подходит.

Raw PUT передаёт непрерывное тело и необязательный X-Upload-Length. HTTP 201 `{committed:true,key,bytes,etag}` появляется после commit хранилища. GET возвращает 200, корректный диапазон — 206; сохраняется [HTTP-контракт](https://github.com/dlorian59/file-streams/blob/main/docs/HTTP-CONTRACT.md). Отмена не равна rollback: неизвестная длина при наблюдаемом EOF может привести к сохранению префикса; финализация/потеря ответа могут оставить результат неизвестным и при известном размере. Одноразовые потоки автоматически не повторяйте.

## Проверка окружения

Нужен браузерный HTTP/2 или HTTP/3 с TLS и сохранением request streaming через реальный reverse proxy. Поддержка HTTP/1 сервером не доказывает поддержку потокового upload браузером. Отключите сжатие ответов на byte/range-маршрутах. Разрешите storage identity только нужный bucket/prefix. Starter не создаёт bucket и не проверяет доступность/права при запуске.

Для разработки используйте [парный TLS-пример](ru.md) и [проверку consumer](../../examples/consumer/README.md). Локальные сертификаты, публичные fixture credentials и тестовая identity — не production-конфигурация. [Проверки и ограничения](../VERIFICATION.md).

[← README](../../README.md) · [Все свойства и ошибки](reference-ru.md)
