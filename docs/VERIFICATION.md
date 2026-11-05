# Verification / Проверки — 2026-11-04

[← Versions / Версии](index.md) · [README](../README.md)

## Documentation follow-up / Дополнение документации — 2026-11-05

Full EN/RU configuration references now cover every property, default, validation rule, conditional bean/backoff/lifecycle, HTTP status and application integration boundary. Compiled override examples show custom limits, credentials/client, handler and mapping order. No starter production behavior changed; only affected example compilation and navigation were checked. The temporary real Boot HTTP/2 fixture also served the React companion's native Action form smoke test and was stopped afterward; it remained test-only memory storage, not S3.

Полные EN/RU справочники описывают все properties/defaults/validation, условия beans/backoff/cleanup, HTTP-статусы и границы интеграции. Override-примеры лимитов, credentials/client, handler и mapping скомпилированы. Production starter не изменялся; повторно проверены только затронутые примеры и навигация. Временный Boot HTTP/2 fixture использовался React companion для native Action form smoke и остановлен после проверки; это тестовое хранилище в памяти, не S3.


| Check / Проверка | Result / Результат |
| --- | --- |
| Spring Boot 4.1.2, Java 17 / 21 / 25 | Six auto-configuration tests pass on each runtime: imports, defaults, typed bounds, invalid settings, backoff, resource lifecycle, HTTP opt-in. / По шесть тестов на каждой JDK. |
| Java 27 | Not run: the installed toolchain symlink points to a missing directory. CI configuration includes 27, but CI has not run. / Не выполнено: локальная ссылка JDK указывает на отсутствующий каталог. |
| Bytecode | Main library and example classes target Java 17 (major 61). / Библиотека и пример собираются для Java 17. |
| Build | JAR, sources, Javadoc, configuration metadata, auto-configuration imports, MIT and runnable example distribution built. Published only to an isolated local Maven directory. / Артефакты собраны и помещены только в локальный Maven-каталог. |
| HTTP routing | Real Boot server: independent `/example/status` RouterFunction returns 200; starter `/objects/missing` returns structured 404. / Обычный маршрут и endpoint starter работают одновременно. |
| Browser → Spring | Actual headless Chromium HTTPS/HTTP2 upload of 45 bytes, confirmed commit and streamed download with byte equality; zero page errors. Destination is a test writable sink. / Реальная браузерная загрузка, commit и совпадающее скачивание. |
| Known-length cancellation | Declared 1 MiB, cancelled before EOF: client `ABORTED` / unknown; subsequent GET 404. / Отмена до EOF при объявленной длине, последующий GET 404. |
| Unknown-length cancellation | Client `ABORTED` / unknown; fixture retained an 8,192-byte prefix. Exact HTTP termination cause is not established. / Отмена с неизвестной длиной оставила префикс 8 192 байта; причина на уровне транспорта не установлена. |
| S3 backend | Blocked: Docker could not start Garage because an existing parent snapshot was missing. No global Docker repair was attempted. / Новый S3 E2E заблокирован ошибкой Docker snapshot. |

[Recorded browser/server observations / Запись наблюдений](evidence/browser-spring.json).

The browser tests use the real starter, server, HTTP handler and browser package with deliberately small in-memory test storage. They do not establish S3 cleanup, throughput, memory bounds for arbitrary deployments or reverse-proxy support. Cancellation is not rollback; when the final size is known, send `knownLength` / `X-Upload-Length` so a short body can be rejected. The fixture has per-file and retained-storage limits and at most two active uploads; it is compiled in a separate `transportFixture` source set and excluded from production distributions.

Браузерные проверки используют настоящий starter, сервер, HTTP-адаптер и браузерный пакет с небольшим тестовым хранилищем в памяти. Они не подтверждают очистку S3, производительность, память произвольного окружения или поддержку reverse proxy. Отмена не является откатом. Когда размер известен, передавайте `knownLength` / `X-Upload-Length`: это позволяет отклонить неполное тело. Fixture ограничивает размер файла, сохранённые данные и две активные загрузки; он вынесен в отдельный source set и не входит в production distribution.

GitHub account access is unavailable in this session; remote repository creation, CI execution and Maven/npm registry publication have not been performed. / Доступ к GitHub недоступен; удалённый репозиторий, CI и публикация в реестры не выполнены.

[← Versions / Версии](index.md) · [README](../README.md)

## 2026-11-05 — integration profiles and real Spring/S3 flow

Added configured browser transfers and shared size profiles without changing the wire protocol. Java defaults remain 16 GiB; browser convenience defaults are 64 MiB attachments. Local artifacts were resolved by independent consumers, not source-project links. No registry publication is implied.

- Base core/WebFlux regression tests, S3 contract tests, Java plain-client consumer and demo compilation passed on JDK 25 with release 17 bytecode.
- Browser tests: 30 passed, including configured facade type/length/auth/range/cancellation/outcome behavior; full annotated examples typechecked and the clean npm-packed consumer passed.
- Starter configuration/backoff/profile override tests passed. The independent existing-application consumer passed through both Gradle and Maven, with actual Boot HTTP + Spring Security Basic test identities and default firewall: anonymous 401, another account 403 before storage, owner PUT 201, GET 200, range 206, oversized upload 413 and disabled generic route 404. Its bounded storage is a test fixture, not S3.
- Separate real React → Spring Boot 4.1.2 HTTPS/HTTP2 → Garage 2.4.1 runs uploaded and downloaded `demo-account:ticket-visual-en.txt` and `demo-account:ticket-visual-ru.txt`, each 131,072 bytes. The browser compared downloaded OPFS bytes and reported no page errors. Direct signed S3 GET independently returned both objects with SHA-256 `02c9a68f567b8ec215d893cde67ae2da41ba3c9b838d0ba4d3d04ab3cdc58da6`.
- Docker's cached snapshot was unavailable. The real S3 run used an isolated official standalone Garage binary and fresh temporary storage, not a Docker repair. Owned Boot/Garage processes were stopped after verification.

This run does not certify AWS, MinIO, arbitrary proxy/browser combinations, a production identity provider or all Java runtime versions. It does not extend earlier cancellation/rollback guarantees; known/unknown outcome boundaries remain as documented. The real S3 demo's generic route is loopback-only and unauthenticated; production account authorization is independently checked by the secured consumer.

### По-русски

Профили и настроенный браузерный клиент используют прежний протокол. Java default — 16 ГиБ, convenience API браузера — attachments 64 МиБ. Проверены локальные артефакты, не публикация в реестре.

Core/WebFlux/S3 contract tests, Java consumer и компиляция demo прошли на JDK 25 с bytecode release17. Браузер: 30 тестов, компиляция комментированных примеров и чистый npm-packed consumer. Starter: binding/backoff и override профиля; отдельный consumer прошёл через Gradle и Maven с настоящими Boot HTTP/Spring Security и стандартным firewall. Проверены 401/403 до storage, PUT201/GET200/range206/limit413/отсутствие общего маршрута404; его хранилище тестовое.

Отдельно React → Spring Boot HTTPS/HTTP2 → настоящий Garage передал два файла EN/RU по 131072 байта. Байты OPFS совпали, page errors отсутствовали; прямой подписанный S3 GET подтвердил оба объекта и SHA-256 выше. Использовался официальный standalone Garage, так как Docker snapshot недоступен; глобальный Docker не ремонтировался. Процессы Boot/Garage остановлены. AWS/MinIO, произвольные прокси/браузеры, production identity provider и все Java runtimes этим запуском не проверялись. Границы отмены/неизвестного результата не изменились. Generic S3 demo без auth ограничено loopback; production-policy отдельно проверена защищённым consumer.
